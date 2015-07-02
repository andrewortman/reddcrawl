package com.andrewortman.reddcrawl.services;

import com.andrewortman.reddcrawl.client.RedditClient;
import com.andrewortman.reddcrawl.client.RedditClientException;
import com.andrewortman.reddcrawl.client.models.RedditStory;
import com.andrewortman.reddcrawl.repository.StoryRepository;
import com.andrewortman.reddcrawl.repository.model.StoryHistoryModel;
import com.andrewortman.reddcrawl.repository.model.StoryModel;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * The story updater crawler is the heart-n-soul of reddcrawl - it fetches the latest histories of the stories
 * and puts it in the database
 */
public class StoryHistoryUpdaterService extends Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoryHistoryUpdaterService.class);

    @Nonnull
    private final RedditClient redditClient;

    @Nonnull
    private final StoryRepository storyRepository;

    @Nonnull
    private final Integer numUpdateWorkers;

    @Nonnull
    private final Integer oldestStoryAgeInSeconds;

    @Nonnull
    private final Integer historyUpdateIntervalInSeconds;

    @Nonnull
    private final Meter historyUpdateMeter;

    @Nonnull
    private final Histogram historyUpdateBatchHistogram;

    public StoryHistoryUpdaterService(@Nonnull final RedditClient redditClient,
                                      @Nonnull final StoryRepository storyRepository,
                                      @Nonnull final Integer numUpdateWorkers,
                                      @Nonnull final Integer oldestStoryAgeInSeconds,
                                      @Nonnull final Integer historyUpdateIntervalInSeconds,
                                      @Nonnull final MetricRegistry metricRegistry) {
        this.redditClient = redditClient;
        this.storyRepository = storyRepository;
        this.numUpdateWorkers = numUpdateWorkers;
        this.oldestStoryAgeInSeconds = oldestStoryAgeInSeconds;
        this.historyUpdateIntervalInSeconds = historyUpdateIntervalInSeconds;
        this.historyUpdateMeter = metricRegistry.meter(MetricRegistry.name("reddcrawl", "story", "history_updates"));
        this.historyUpdateBatchHistogram = metricRegistry.histogram(MetricRegistry.name("reddcrawl", "story", "history_update_batch_size"));
    }

    @Override
    public void runIteration() throws Exception {
        while (!interrupted()) {
            //we use multiple threads here because this can (sometimes) be a slow operation. Since each of these batch
            //requests are stateless, we can queue up several batches of stories to be done at once. If one of them is slow
            //and times out, the time it was running is no longer wasted as it more than likely the other requests made it through and
            //we didn't loose any requests in the token bucket due to overflow
            LOGGER.info("finding front page stories using " + numUpdateWorkers + " workers.");

            final Date minTimeAgo = new Date(new Date().getTime() - TimeUnit.SECONDS.toMillis(this.historyUpdateIntervalInSeconds)); //stories dont update sooner than 2 minutes
            final Date maxTimeAgo = new Date(new Date().getTime() - TimeUnit.SECONDS.toMillis(this.oldestStoryAgeInSeconds)); //dont update past 2 days old

            //request a big batch of stories up to worker count * max listing size
            final List<StoryModel> storiesNeedingUpdate =
                    storyRepository.findStoriesNeedingUpdate(maxTimeAgo, minTimeAgo, this.numUpdateWorkers * RedditClient.MAX_ITEMS_PER_LISTING_PAGE);

            //update the histogram so we can see when we are saturating the batch size or not
            historyUpdateBatchHistogram.update(storiesNeedingUpdate.size());

            if (storiesNeedingUpdate.size() == 0) {
                LOGGER.info("no stories needing updating - bailing");
                return;
            }

            //batch it up using guava
            final List<List<StoryModel>> storiesNeedingUpdateBatched =
                    Lists.partition(storiesNeedingUpdate, RedditClient.MAX_ITEMS_PER_LISTING_PAGE);

            //then create a thread for each batch and kick off the job
            final List<Thread> workerThreads = new ArrayList<>(storiesNeedingUpdateBatched.size());
            for (final List<StoryModel> storyBatchItem : storiesNeedingUpdateBatched) {
                final Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                    try {
                        //in each thread, we are going to first convert the story map into a lookup table
                        final HashMap<String, StoryModel> storyModelLookup = new HashMap<>();
                        for (final StoryModel storyModel : storyBatchItem) {
                            storyModelLookup.put(storyModel.getRedditShortId(), storyModel);
                        }

                        //and then we will request the list of story ids to be updated via the redditclient
                        LOGGER.info("Updating " + storyModelLookup.size() + " stories");
                        final Map<String, RedditStory> storiesUpdated = redditClient.getStoriesById(storyModelLookup.keySet());
                        LOGGER.info("Received back " + storiesUpdated.size() + " stories from reddit");
                        //then we will create story history items with them
                        for (final RedditStory updatedRedditStory : storiesUpdated.values()) {
                            final StoryHistoryModel newHistoryItem = new StoryHistoryModel();
                            newHistoryItem.setTimestamp(new Date());
                            newHistoryItem.setScore(updatedRedditStory.getScore());
                            newHistoryItem.setHotness(updatedRedditStory.getHotness());
                            newHistoryItem.setComments(updatedRedditStory.getNumComments());
                            newHistoryItem.setGilded(updatedRedditStory.getGilded());

                            //and then store that history item in the database
                            storyRepository.addStoryHistory(storyModelLookup.get(updatedRedditStory.getId()), newHistoryItem);
                            LOGGER.trace("Updated history for " + updatedRedditStory.getId());
                            historyUpdateMeter.mark();
                        }
                    } catch (final RedditClientException redditClientException) {
                        //catch point - if a RCE is emitted we are just going to ignore this batch and emit an error to log
                        //the batch will be in the next iteration to be retried
                        LOGGER.error("Worker received RCE: " + redditClientException);
                    }
                    }
                });

                //add the worker thread to the list and kick it off
                workerThreads.add(thread);
                thread.start();
            }

            //join all the threads together
            for (final Thread thread : workerThreads) {
                //todo: should we add a timeout in here? use a executor pool and do futures?
                thread.join();
            }
        }
    }

    @Override
    public int getMinimumRepetitionTimeInSeconds() {
        return 10; //10 seconds wait time if there are not stories needing update (try to queue up some stories)
    }
}
