package com.andrewortman.reddcrawl.services;

import com.andrewortman.reddcrawl.client.RedditClient;
import com.andrewortman.reddcrawl.client.RedditClientException;
import com.andrewortman.reddcrawl.client.models.RedditSubreddit;
import com.andrewortman.reddcrawl.repository.SubredditRepository;
import com.andrewortman.reddcrawl.repository.model.SubredditHistoryModel;
import com.andrewortman.reddcrawl.repository.model.SubredditModel;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.ws.rs.ForbiddenException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This service will simply go out and fetch the details about known subreddits create new history items for them
 * in the database
 */
public class SubredditHistoryUpdaterService extends Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubredditHistoryUpdaterService.class);

    @Nonnull
    private final RedditClient redditClient;

    @Nonnull
    private final SubredditRepository subredditRepository;

    @Nonnull
    private final Meter historyUpdateMeter;

    private final int updateIntervalSeconds;

    public SubredditHistoryUpdaterService(@Nonnull final RedditClient redditClient,
                                          @Nonnull final SubredditRepository subredditRepository,
                                          @Nonnull final MetricRegistry metricRegistry,
                                          final int updateIntervalSeconds) {
        this.redditClient = redditClient;
        this.subredditRepository = subredditRepository;
        this.updateIntervalSeconds = updateIntervalSeconds;

        //metrics
        this.historyUpdateMeter = metricRegistry.meter(MetricRegistry.name("reddcrawl", "subreddit", "history", "updates"));
    }

    @Override
    public void runIteration() throws Exception {
        //hack: need way of doing this without a "divide by 2" window
        final Date latestDate = new Date(new Date().getTime() - TimeUnit.SECONDS.toMillis(updateIntervalSeconds) / 2);

        final List<SubredditModel> subredditsNeedingUpdate = subredditRepository.findSubredditsNeedingUpdate(latestDate);

        for (final SubredditModel subredditModel : subredditsNeedingUpdate) {
            LOGGER.info("Fetching subreddit details for subreddit " + subredditModel.getName());
            final RedditSubreddit redditSubreddit;

            try {
                redditSubreddit = redditClient.getSubredditByName(subredditModel.getName());
            } catch (@Nonnull final RedditClientException redditClientException) {
                //if the subreddit went private, we'll receive a forbidden exception, so we need to handle that special case
                //I had to do this when IAMA went private on July 2nd, 2015
                if (redditClientException.getCause() instanceof ForbiddenException) {
                    LOGGER.warn("Received forbidden exception when fetching subreddit details for " + subredditModel.getName());
                    continue;
                } else {
                    //rethrow
                    throw redditClientException;
                }
            }

            LOGGER.info("Saving subreddit history for subreddit " + subredditModel.getName());

            final SubredditHistoryModel historyModel = new SubredditHistoryModel();
            historyModel.setSubreddit(subredditModel);
            historyModel.setTimestamp(new Date());
            historyModel.setSubscribers(redditSubreddit.getSubscribers());
            historyModel.setActive(redditSubreddit.getActive());
            historyModel.setCommentHideMins(redditSubreddit.getCommentScoreHideMins());
            subredditRepository.addSubredditHistory(subredditModel, historyModel);
            historyUpdateMeter.mark();
        }
    }

    @Override
    public int getMinimumRepetitionTimeInSeconds() {
        return updateIntervalSeconds; //every 30 minutes
    }

    @Override
    public int getRepeatDelayInSecondsIfExceptionOccurred() {
        return 5; //retry in 5 seconds if there was a failure at scraping the history of the subreddits
    }
}
