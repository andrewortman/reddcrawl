package com.andrewortman.reddcrawl.services;

import com.andrewortman.reddcrawl.client.RedditClient;
import com.andrewortman.reddcrawl.client.models.RedditSubreddit;
import com.andrewortman.reddcrawl.repository.SubredditRepository;
import com.andrewortman.reddcrawl.repository.model.SubredditModel;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * This service will occasionally get a list of the subreddits found on the front page and appends those to
 * a set of all front page subreddits. This way we can figure out which subreddits to track over time
 */
public class NewSubredditScraperService extends Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewSubredditScraperService.class);

    @Nonnull
    private final RedditClient redditClient;

    @Nonnull
    private final SubredditRepository subredditRepository;

    @Nonnull
    private final Meter subredditDiscoveryMeter;

    public NewSubredditScraperService(@Nonnull final RedditClient redditClient,
                                      @Nonnull final SubredditRepository subredditRepository,
                                      @Nonnull final MetricRegistry metricRegistry) {
        this.redditClient = redditClient;
        this.subredditRepository = subredditRepository;

        //metrics
        this.subredditDiscoveryMeter = metricRegistry.meter(MetricRegistry.name("reddcrawl", "subreddit", "discovered"));
    }

    @Override
    public void runIteration() throws Exception {
        LOGGER.info("fetching list of front page subreddits");
        final Set<String> subredditsOnFrontPage = redditClient.getDefaultFrontPageSubreddits();

        LOGGER.info("front page subreddits are now " + Joiner.on("+").join(subredditsOnFrontPage));
        for (final String discoveredSubredditName : subredditsOnFrontPage) {
            final SubredditModel subredditModel = subredditRepository.findSubredditByName(discoveredSubredditName);
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

                subredditRepository.saveNewSubreddit(newSubredditModel);
                this.subredditDiscoveryMeter.mark();
            }
        }
    }

    @Override
    public int getMinimumRepetitionTimeInSeconds() {
        return 3 * 60 * 60; //every 3 hours
    }
}
