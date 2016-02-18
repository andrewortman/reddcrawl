package com.andrewortman.reddcrawl.services;

import com.andrewortman.reddcrawl.archive.JsonArchive;
import com.andrewortman.reddcrawl.archive.JsonArchiveEventHandler;
import com.andrewortman.reddcrawl.repository.StoryRepository;
import com.andrewortman.reddcrawl.json.StoryJsonBuilder;
import com.andrewortman.reddcrawl.repository.model.StoryModel;
import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;

public class StoryArchivingService extends Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryArchivingService.class);

    //date format for directories
    private static final FastDateFormat DATE_FORMAT = FastDateFormat.getInstance("yyyy-MM-dd", TimeZone.getTimeZone("UTC"));

    private final int secondsAfterCreateDateToArchive;

    private final int secondsBetweenArchiveBatches;

    private final int maxStoryBatchSize;

    @Nonnull
    private final StoryRepository storyRepository;

    @Nonnull
    private final Counter storiesArchivedCounter;

    @Nonnull
    private final JsonArchive jsonArchive;


    public StoryArchivingService(@Nonnull final StoryRepository storyRepository,
                                 final int secondsAfterCreateDateToArchive,
                                 final int secondsBetweenArchiveBatches,
                                 final int maxStoryBatchSize,
                                 @Nonnull final MetricRegistry metricRegistry,
                                 @Nonnull final JsonArchive jsonArchive) {

        this.storyRepository = storyRepository;
        this.secondsAfterCreateDateToArchive = secondsAfterCreateDateToArchive;
        this.secondsBetweenArchiveBatches = secondsBetweenArchiveBatches;
        this.maxStoryBatchSize = maxStoryBatchSize;
        this.jsonArchive = jsonArchive;
        this.storiesArchivedCounter = metricRegistry.counter(MetricRegistry.name("reddcrawl", "story", "archives"));
    }

    @Override
    public void runIteration() throws Exception {
        final Date lastCreateDate = new Date(new Date().getTime() - secondsAfterCreateDateToArchive * 1000L);
        LOGGER.info("Archiving all stories before " + lastCreateDate.toString());

        while (!Thread.currentThread().isInterrupted()) {
            final List<StoryModel> archivableStories = storyRepository.findArchivableStories(lastCreateDate, maxStoryBatchSize);

            if (archivableStories.size() == 0) {
                LOGGER.debug("All stories archived - all done.");
                break;
            }

            LOGGER.debug("Archiving batch of " + archivableStories.size() + " stories");

            final Multimap<String, JsonNode> archiveNodesByDate = HashMultimap.create();

            //used to look up the storymodel from the json node after sending it through the archiver
            final Map<JsonNode, StoryModel> jsonNodeStoryModelMap = new HashMap<>();

            for (final StoryModel storyModel : archivableStories) {
                LOGGER.debug("Archiving story " + storyModel.getRedditShortId());
                final String dateString = DATE_FORMAT.format(storyModel.getCreatedAt());
                final JsonNode jsonNode = StoryJsonBuilder.renderJsonDetailForStory(storyModel, storyRepository.getStoryHistory(storyModel));
                archiveNodesByDate.put(dateString, jsonNode);
                jsonNodeStoryModelMap.put(jsonNode, storyModel); //put a reference of the json node -> story model into a map so we can delete it with the event handler
            }

            //write the nodes, and pass in an event handler to clean up from the database
            jsonArchive.writeNodes(archiveNodesByDate, new JsonArchiveEventHandler() {
                @Override
                public void handleArchiveComplete(@Nonnull Collection<JsonNode> completedJsonNodes) {
                    List<StoryModel> deletableStories = new ArrayList<>(completedJsonNodes.size());
                    for (final JsonNode jsonNode : completedJsonNodes) {
                        deletableStories.add(jsonNodeStoryModelMap.get(jsonNode));
                    }

                    storyRepository.deleteStories(deletableStories);
                    storiesArchivedCounter.inc(deletableStories.size());
                }
            });

            try {
                Thread.sleep(secondsBetweenArchiveBatches * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public int getMinimumRepetitionTimeInSeconds() {
        return 15 * 60; //every 15 minutes do a story dump to archive
    }

    @Override
    public int getRepeatDelayInSecondsIfExceptionOccurred() {
        return 30; //retry after 30 seconds
    }
}
