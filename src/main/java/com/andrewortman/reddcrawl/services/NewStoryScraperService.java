package com.andrewortman.reddcrawl.services;

import com.andrewortman.reddcrawl.client.RedditClient;
import com.andrewortman.reddcrawl.client.RedditClientException;
import com.andrewortman.reddcrawl.client.models.RedditStory;
import com.andrewortman.reddcrawl.repository.StoryRepository;
import com.andrewortman.reddcrawl.repository.SubredditRepository;
import com.andrewortman.reddcrawl.repository.model.StoryHistoryModel;
import com.andrewortman.reddcrawl.repository.model.StoryModel;
import com.andrewortman.reddcrawl.repository.model.SubredditModel;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    //max number of stories to pull from both /new
    private final int scavengeNewStoryCount;

    //max number of stories to pull from both /hot
    private final int scavengeHotStoryCount;

    //number of seconds in which a subreddit expires from the search (probably close to a week or so to handle things like /blog and /announcements
    private final int subredditExpirationInterval;

    //number of seconds since the last history update a story can be autoupdated by the scraper
    //(this is to help the story history updater from updating stories still seen in either the /hot or /new list)
    private final int autoUpdateHistoryInterval;

    //number of seconds between fetches
    private final int checkInterval;

    @Nonnull
    private final Meter storyDiscoveredMeter;

    @Nonnull
    private final Histogram storyDiscoveredCreatedTimeHistogram;

    @Nonnull
    private final Meter autoHistoryUpdateMeter;

    public NewStoryScraperService(@Nonnull final RedditClient redditClient,
                                  @Nonnull final StoryRepository storyRepository,
                                  @Nonnull final SubredditRepository subredditRepository,
                                  final int scavengeNewStoryCount,
                                  final int scavengeHotStoryCount,
                                  final int subredditExpirationInterval,
                                  final int autoUpdateHistoryInterval,
                                  final int checkInterval,
                                  @Nonnull final MetricRegistry metricRegistry) {
        this.redditClient = redditClient;
        this.storyRepository = storyRepository;
        this.subredditRepository = subredditRepository;
        this.scavengeNewStoryCount = scavengeNewStoryCount;
        this.scavengeHotStoryCount = scavengeHotStoryCount;
        this.subredditExpirationInterval = subredditExpirationInterval;
        this.autoUpdateHistoryInterval = autoUpdateHistoryInterval;
        this.checkInterval = checkInterval;
        this.storyDiscoveredMeter = metricRegistry.meter(MetricRegistry.name("reddcrawl", "story", "discovered"));
        this.storyDiscoveredCreatedTimeHistogram = metricRegistry.histogram(MetricRegistry.name("reddcrawl", "story", "discovered", "time"));
        this.autoHistoryUpdateMeter = metricRegistry.meter(MetricRegistry.name("reddcrawl", "story", "history", "autoupdate"));
    }

    @Override
    public void runIteration() throws Exception {
        //get the subreddits we need to scan
        LOGGER.info("scavenging the hottest stories in the past hour");
        final Map<String, SubredditModel> subreddits = new HashMap<>();
        final Date expirationDate = new Date(new Date().getTime() - TimeUnit.SECONDS.toMillis(subredditExpirationInterval));
        final List<SubredditModel> subredditModels = subredditRepository.getAllRecentlySeenSubreddits(expirationDate);
        for (final SubredditModel subredditModel : subredditModels) {
            subreddits.put(subredditModel.getName(), subredditModel);
        }

        if (subreddits.size() == 0) {
            throw new RedditClientException("No subreddits in database - no idea what to fetch for");
        }

        //get the top N hot stories in an aggregated view of those subreddits
        final Set<RedditStory> hotStories = redditClient.getStoryListingForSubreddits(subreddits.keySet(),
                RedditClient.SortStyle.TOP, RedditClient.TimeRange.HOUR, this.scavengeHotStoryCount);

        //get the top N new stories in an aggregated view of those subreddits
        final Set<RedditStory> newStories = redditClient.getStoryListingForSubreddits(subreddits.keySet(),
                RedditClient.SortStyle.NEW, RedditClient.TimeRange.ALL, this.scavengeNewStoryCount);

        final Set<RedditStory> stories = Sets.union(hotStories, newStories);

        for (final RedditStory story : stories) {
            //check if the story already exists, and if it does, bail out
            final StoryModel foundStory = storyRepository.findStoryByRedditShortId(story.getId());
            if (foundStory != null) {
                final Date minDateForAutoupdate = new Date(new Date().getTime() - TimeUnit.SECONDS.toMillis(autoUpdateHistoryInterval));
                if (foundStory.getUpdatedAt().before(minDateForAutoupdate)) {
                    LOGGER.debug("Auto-updating history for story " + story.getId());

                    final StoryHistoryModel historyModel = new StoryHistoryModel();
                    historyModel.setTimestamp(new Date());
                    historyModel.setScore(story.getScore());
                    historyModel.setHotness(story.getHotness());
                    historyModel.setComments(story.getNumComments());
                    historyModel.setGilded(story.getGilded());

                    storyRepository.addStoryHistory(foundStory, historyModel);
                    this.autoHistoryUpdateMeter.mark();
                } else {
                    LOGGER.debug("ignoring story " + story.getId() + " because it already exists in db and doesn't need an autoupdate");
                }

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

            //mark the discovery
            storyDiscoveredMeter.mark();

            //mark the discovery time so we can measure the min/max/median discovery times
            storyDiscoveredCreatedTimeHistogram.update(new Date().getTime() - storyModel.getCreatedAt().getTime());

            LOGGER.info("saved new story " + story.getId());
        }
    }

    @Override
    public int getMinimumRepetitionTimeInSeconds() {
        return checkInterval;
    }

    @Override
    public int getRepeatDelayInSecondsIfExceptionOccurred() {
        return 5; //try again after 5 seconds on an exception
    }
}
