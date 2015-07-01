package com.andrewortman.reddcrawl.services;

import com.andrewortman.reddcrawl.client.RedditClient;
import com.andrewortman.reddcrawl.client.models.RedditSubreddit;
import com.andrewortman.reddcrawl.repository.SubredditDao;
import com.andrewortman.reddcrawl.repository.model.SubredditHistoryModel;
import com.andrewortman.reddcrawl.repository.model.SubredditModel;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * This service will occasionally get a list of the subreddits found on the front page and appends those to
 * a set of all front page subreddits. This way we can figure out which subreddits to track over time
 */
public class SubredditScraperService extends Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubredditScraperService.class);

    @Nonnull
    private final RedditClient redditClient;

    @Nonnull
    private final SubredditDao subredditDao;

    @Nonnull
    private final Set<String> subreddits;

    @Nonnull
    private final Meter historyUpdateMeter;

    public SubredditScraperService(@Nonnull final RedditClient redditClient,
                                   @Nonnull final SubredditDao subredditDao,
                                   @Nonnull final MetricRegistry metricRegistry) {
        this.redditClient = redditClient;
        this.subredditDao = subredditDao;
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
    public void runIteration() throws Exception {
        //todo - clean this up

        LOGGER.info("fetching latest details on front page subreddits");
        final Set<String> newSubreddits = redditClient.getDefaultFrontPageSubreddits();

        //in a lock (because this list is accessed by other crawlers in memory),
        //make sure the subreddits are persisted in the subreddit db
        synchronized (subreddits) {
            subreddits.addAll(newSubreddits);
            LOGGER.info("front page subreddits are now " + Joiner.on("+").join(subreddits));
            for (final String discoveredSubredditName : newSubreddits) {
                final SubredditModel subredditModel = subredditDao.findSubredditByName(discoveredSubredditName);
                if (subredditModel == null) {
                    LOGGER.info("Discovered new front page subreddit! name=" + discoveredSubredditName);
                    final RedditSubreddit redditSubreddit = redditClient.getSubredditByName(discoveredSubredditName);
                    final SubredditModel newSubredditModel = new SubredditModel();
                    newSubredditModel.setSubredditShortId(redditSubreddit.getId());
                    newSubredditModel.setUrl(redditSubreddit.getUrl());
                    newSubredditModel.setDescription(redditSubreddit.getDescription());
                    newSubredditModel.setSummary(redditSubreddit.getPublicDescription());
                    newSubredditModel.setSubmissionType(redditSubreddit.getSubmissionType());
                    newSubredditModel.setName(redditSubreddit.getName());
                    newSubredditModel.setTitle(redditSubreddit.getTitle());

                    subredditDao.saveNewSubreddit(newSubredditModel);
                }
            }
        }

        //outside of the lock, we can update the story histories of all subreddits
        for (final String subredditName : subreddits) {
            LOGGER.info("Fetching subreddit details for subreddit " + subredditName);
            final RedditSubreddit redditSubreddit = redditClient.getSubredditByName(subredditName);
            LOGGER.info("Saving subreddit history for subreddit " + subredditName);

            final SubredditModel subredditModel = subredditDao.findSubredditByName(subredditName);

            final SubredditHistoryModel historyModel = new SubredditHistoryModel();
            historyModel.setSubreddit(subredditModel);
            historyModel.setTimestamp(new Date());
            historyModel.setSubscribers(redditSubreddit.getSubscribers());
            historyModel.setActive(redditSubreddit.getActive());
            historyModel.setCommentHideMins(redditSubreddit.getCommentScoreHideMins());
            subredditDao.addSubredditHistory(subredditModel, historyModel);
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
