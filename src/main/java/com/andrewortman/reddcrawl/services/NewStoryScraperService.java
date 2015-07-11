package com.andrewortman.reddcrawl.services;

import com.andrewortman.reddcrawl.client.RedditClient;
import com.andrewortman.reddcrawl.client.RedditClientException;
import com.andrewortman.reddcrawl.client.models.RedditStory;
import com.andrewortman.reddcrawl.repository.StoryRepository;
import com.andrewortman.reddcrawl.repository.SubredditRepository;
import com.andrewortman.reddcrawl.repository.model.StoryHistoryModel;
import com.andrewortman.reddcrawl.repository.model.StoryModel;
import com.andrewortman.reddcrawl.repository.model.SubredditModel;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * This service will scrape the top stories for the hour and add newly discovered stories to the database
 */
public class NewStoryScraperService extends Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewStoryScraperService.class);

    @Nonnull
    private final RedditClient redditClient;

    @Nonnull
    private final StoryRepository storyRepository;

    @Nonnull
    private final SubredditRepository subredditRepository;

    private final int scavengeStoryCount;

    private final int subredditExpirationHours;

    @Nonnull
    private final Meter storyDiscoveredMeter;

    public NewStoryScraperService(@Nonnull final RedditClient redditClient,
                                  @Nonnull final StoryRepository storyRepository,
                                  @Nonnull final SubredditRepository subredditRepository,
                                  final int scavengeStoryCount,
                                  final int subredditExpirationHours,
                                  @Nonnull final MetricRegistry metricRegistry) {
        this.redditClient = redditClient;
        this.storyRepository = storyRepository;
        this.subredditRepository = subredditRepository;
        this.scavengeStoryCount = scavengeStoryCount;
        this.subredditExpirationHours = subredditExpirationHours;
        this.storyDiscoveredMeter = metricRegistry.meter(MetricRegistry.name("reddcrawl", "story", "discovered"));
    }

    @Override
    public void runIteration() throws Exception {
        //get the subreddits we need to scan
        LOGGER.info("scavenging the hottest stories in the past hour");
        final Map<String, SubredditModel> subreddits = new HashMap<>();
        final Date expirationDate = new Date(new Date().getTime() - TimeUnit.HOURS.toMillis(subredditExpirationHours));
        final List<SubredditModel> subredditModels = subredditRepository.getAllRecentlySeenSubreddits(expirationDate);
        for (final SubredditModel subredditModel : subredditModels) {
            subreddits.put(subredditModel.getName(), subredditModel);
        }

        if (subreddits.size() == 0) {
            throw new RedditClientException("No subreddits in database - no idea what to fetch for");
        }

        //get the top N stories in an aggregated view of those subreddits
        final Set<RedditStory> stories = redditClient.getStoryListingForSubreddits(subreddits.keySet(),
                RedditClient.SortStyle.TOP, RedditClient.TimeRange.HOUR, this.scavengeStoryCount);

        for (final RedditStory story : stories) {
            //check if the story already exists, and if it does, bail out
            final StoryModel foundStory = storyRepository.findStoryByRedditShortId(story.getId());
            if (foundStory != null) {
                LOGGER.debug("ignoring story " + story.getId() + " because it already exists in db");
                continue;
            }

            if (!subreddits.containsKey(story.getSubreddit())) {
                LOGGER.error("Subreddit `" + story.getSubreddit() + "` does not exist yet - will not persist story " + story.getId() + " to DB");
                continue;
            }

            //create the story model
            final StoryModel storyModel = new StoryModel();
            storyModel.setRedditShortId(story.getId());
            storyModel.setCreatedAt(story.getCreatedAt());
            storyModel.setTitle(story.getTitle());
            storyModel.setAuthor(story.getAuthor());
            storyModel.setSubreddit(subreddits.get(story.getSubreddit()));
            storyModel.setUrl(story.getUrl());
            storyModel.setDomain(story.getDomain());
            storyModel.setThumbnail(story.getThumbnail());
            storyModel.setPermalink(story.getPermalink());
            storyModel.setIsSelf(story.getIsSelf());
            storyModel.setSelftext(story.getSelftext());
            storyModel.setDistinguished(story.getDistinguished());
            storyModel.setOver18(story.getOver18());
            storyModel.setStickied(story.getStickied());

            //create the history model - this only needs the basics, the rest is linked up in saveNewStory
            final StoryHistoryModel historyModel = new StoryHistoryModel();
            historyModel.setTimestamp(new Date());
            historyModel.setScore(story.getScore());
            historyModel.setHotness(story.getHotness());
            historyModel.setComments(story.getNumComments());
            historyModel.setGilded(story.getGilded());

            //save!
            storyRepository.saveNewStory(storyModel, historyModel);
            storyDiscoveredMeter.mark();
            LOGGER.info("saved new story " + story.getId());
        }
    }

    @Override
    public int getMinimumRepetitionTimeInSeconds() {
        return 60; //only get the latest stories up to every minute at a time
    }

    @Override
    public int getRepeatDelayInSecondsIfExceptionOccurred() {
        return 5; //try again after 5 seconds on an exception
    }
}
