package com.andrewortman.reddcrawl.services;

import com.andrewortman.reddcrawl.repository.StoryRepository;
import com.andrewortman.reddcrawl.repository.model.StoryModel;
import com.andrewortman.reddcrawl.services.archive.JsonArchive;
import com.andrewortman.reddcrawl.services.archive.StoryJsonBuilder;
import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StoryArchivingService extends Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoryArchivingService.class);

    private final int secondsAfterCreateDateToArchive;

    private final int secondsBetweenArchiveBatches;

    @Nonnull
    private final StoryRepository storyRepository;

    @Nonnull
    private final Counter storiesArchivedCounter;

    @Nonnull
    private final JsonArchive jsonArchive;


    public StoryArchivingService(@Nonnull final StoryRepository storyRepository,
                                 final int secondsAfterCreateDateToArchive,
                                 final int secondsBetweenArchiveBatches,
                                 @Nonnull final MetricRegistry metricRegistry,
                                 @Nonnull final JsonArchive jsonArchive) {

        this.storyRepository = storyRepository;
        this.secondsAfterCreateDateToArchive = secondsAfterCreateDateToArchive;
        this.secondsBetweenArchiveBatches = secondsBetweenArchiveBatches;
        this.jsonArchive = jsonArchive;

        this.storiesArchivedCounter = metricRegistry.counter(MetricRegistry.name("reddcrawl", "story", "archives"));
    }

    @Override
    public void runIteration() throws Exception {
        //pretty simple, first get a list of stories that need archiving

        final int BATCH_SIZE = 50;
        final Date lastCreateDate = new Date(new Date().getTime() - secondsAfterCreateDateToArchive * 1000L);
        LOGGER.info("Archiving all stories before " + lastCreateDate.toString());

        while (true) {
            final List<StoryModel> archivableStories = storyRepository.findArchivableStories(lastCreateDate, BATCH_SIZE);

            if (archivableStories.size() == 0) {
                LOGGER.debug("All stories archived - all done.");
                break;
            }

            LOGGER.debug("Archiving batch of " + archivableStories.size() + " stories");

            final List<JsonNode> archiveNodes = new ArrayList<>();

            final String archiveName = lastCreateDate.getTime() + "-archive";

            for (final StoryModel storyModel : archivableStories) {
                LOGGER.info("archiving story " + storyModel.getRedditShortId());
                archiveNodes.add(StoryJsonBuilder.renderJsonDetailForStory(storyModel, storyRepository.getStoryHistory(storyModel)));
            }

            jsonArchive.writeJsonNodes(archiveName, archiveNodes);
            storyRepository.deleteStories(archivableStories);
            storiesArchivedCounter.inc(archivableStories.size());
            Thread.sleep(secondsBetweenArchiveBatches * 1000L);
        }
    }

    @Override
    public int getMinimumRepetitionTimeInSeconds() {
        return 60 * 60; //every hour do a story dump to disk
    }

    @Override
    public int getRepeatDelayInSecondsIfExceptionOccurred() {
        return 30; //retry after 30 seconds
    }
}
