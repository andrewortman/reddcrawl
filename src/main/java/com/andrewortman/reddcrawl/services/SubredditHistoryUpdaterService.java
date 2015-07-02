package com.andrewortman.reddcrawl.services;

import com.andrewortman.reddcrawl.client.RedditClient;
import com.andrewortman.reddcrawl.client.models.RedditSubreddit;
import com.andrewortman.reddcrawl.repository.SubredditRepository;
import com.andrewortman.reddcrawl.repository.model.SubredditHistoryModel;
import com.andrewortman.reddcrawl.repository.model.SubredditModel;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.transaction.Transactional;
import java.util.*;

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
    private final Set<String> subreddits;

    @Nonnull
    private final Meter historyUpdateMeter;

    public SubredditHistoryUpdaterService(@Nonnull final RedditClient redditClient,
                                          @Nonnull final SubredditRepository subredditRepository,
                                          @Nonnull final MetricRegistry metricRegistry) {
        this.redditClient = redditClient;
        this.subredditRepository = subredditRepository;
        this.subreddits = new HashSet<>();

        //metrics
        this.historyUpdateMeter = metricRegistry.meter(MetricRegistry.name("reddcrawl", "subreddit", "history_updates"));
        metricRegistry.register(MetricRegistry.name("reddcrawl", "subreddit", "tracked"),
                new Gauge<Integer>() {
                    @Override
                    public Integer getValue() {
                        return subreddits.size();
                    }
                });
    }

    public Set<String> getSubreddits() {
        return subreddits;
    }

    @Override
    @Transactional
    public void runIteration() throws Exception {

        final List<String> subredditNames = subredditRepository.getAllSubredditNames();
        final Map<SubredditModel, SubredditHistoryModel> historyModelMap = new HashMap<>(subredditNames.size());

        for (final String subredditName : subredditNames) {
            LOGGER.info("Fetching subreddit details for subreddit " + subredditName);
            final RedditSubreddit redditSubreddit = redditClient.getSubredditByName(subredditName);
            LOGGER.info("Saving subreddit history for subreddit " + subredditName);

            final SubredditModel subredditModel = subredditRepository.findSubredditByName(subredditName);

            final SubredditHistoryModel historyModel = new SubredditHistoryModel();
            historyModel.setSubreddit(subredditModel);
            historyModel.setTimestamp(new Date());
            historyModel.setSubscribers(redditSubreddit.getSubscribers());
            historyModel.setActive(redditSubreddit.getActive());
            historyModel.setCommentHideMins(redditSubreddit.getCommentScoreHideMins());
            historyModelMap.put(subredditModel, historyModel);
            subredditRepository.addSubredditHistory(subredditModel, historyModel);
            historyUpdateMeter.mark();
        }
    }

    @Override
    public int getMinimumRepetitionTimeInSeconds() {
        return 30 * 60; //every 30 minutes
    }

    @Override
    public int getRepeatDelayInSecondsIfExceptionOccurred() {
        return 5; //retry in 5 seconds if there was a failure at scraping the front page
    }
}
