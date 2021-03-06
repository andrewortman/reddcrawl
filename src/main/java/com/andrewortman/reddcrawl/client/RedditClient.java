package com.andrewortman.reddcrawl.client;

import com.andrewortman.reddcrawl.client.authentication.AuthenticatingRequestFilter;
import com.andrewortman.reddcrawl.client.models.RedditListing;
import com.andrewortman.reddcrawl.client.models.RedditStory;
import com.andrewortman.reddcrawl.client.models.RedditSubreddit;
import com.andrewortman.reddcrawl.client.models.RedditThing;
import com.andrewortman.reddcrawl.client.models.meta.RedditKind;
import com.andrewortman.reddcrawl.client.ratelimiting.RateLimiter;
import com.andrewortman.reddcrawl.client.ratelimiting.RateLimitingClientRequestFilter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * RedditClient is the class used to actually communicate with the API. It handles
 * everything reddcrawl needs to crawl reddit including JSON parsing and rate limiting.
 */
public class RedditClient {
    public static final int MAX_ITEMS_PER_LISTING_PAGE = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(RedditClient.class);

    @Nonnull
    private final Meter clientExceptionMeter;

    @Nonnull
    private final WebTarget redditEndpoint;

    public RedditClient(@Nonnull final RedditClientOptions clientOptions,
                        @Nonnull final RateLimiter rateLimiter,
                        @Nonnull final AuthenticatingRequestFilter authenticatingRequestFilter,
                        @Nonnull final MetricRegistry metricRegistry) {

        this.clientExceptionMeter = metricRegistry.meter(MetricRegistry.name("reddcrawl", "client", "exceptions"));

        //add features in order from last filter -> first filter
        this.redditEndpoint = ClientBuilder.newClient()
                .property(ClientProperties.READ_TIMEOUT, clientOptions.getReadTimeout())
                .property(ClientProperties.CONNECT_TIMEOUT, clientOptions.getConnectTimeout())
                .register(new RequestLoggingFeature(metricRegistry))
                .register(authenticatingRequestFilter)
                .register(new RateLimitingClientRequestFilter(rateLimiter))
                .register(new UserAgentClientRequestFilter(clientOptions.getUserAgent()))
                .target(clientOptions.getQueryEndpoint());
    }

    /**
     * Get a story listing for a given set of subreddits
     *
     * @param subreddits the subreddits to look at
     * @param sort       the sort style
     * @param timeRange  the time range to filter on
     * @param limit      the max number of stories
     * @return set of reddit stories found in the search
     * @throws RedditClientException
     */
    @Nonnull
    public Set<RedditStory> getStoryListingForSubreddits(@Nonnull final Set<String> subreddits,
                                                         @Nonnull final SortStyle sort,
                                                         @Nonnull final TimeRange timeRange,
                                                         final int limit) throws RedditClientException {
        final Set<RedditStory> stories = new LinkedHashSet<>();
        String currentAfter = "";
        int lastCount = 0;
        while (stories.size() < limit) {
            final RedditListing<RedditStory> subListing;
            try {
                final JsonNode jsonNodeResponse =
                        redditEndpoint.path("/r/" + Joiner.on("+").join(subreddits) + "/" + sort.toString() + ".json")
                                .queryParam("limit", MAX_ITEMS_PER_LISTING_PAGE)
                                .queryParam("after", currentAfter)
                                .queryParam("t", timeRange.toString())
                                .request(MediaType.APPLICATION_JSON)
                                .get(JsonNode.class);
                subListing = new RedditListing<>(jsonNodeResponse, RedditStory.class);
            } catch (@Nonnull RedirectionException | ProcessingException | ClientErrorException | JsonProcessingException e) {
                this.clientExceptionMeter.mark();
                throw new RedditClientException(e);
            }

            if (subListing.getChildren().size() == 0) {
                break; //no more listing!
            }

            for (int i = 0; i < subListing.getChildren().size() && stories.size() < limit; i++) {
                stories.add(subListing.getChildren().get(i));
            }

            if (stories.size() == lastCount) {
                break; //no more stories added, fail early
            }

            lastCount = stories.size();
            currentAfter = subListing.getAfter();
        }

        return stories;
    }

    /**
     * Gets the current set of front page stories
     *
     * @param limit max number of front page stories to return
     * @return set of reddit stories in order seen (order probably shouldn't be trusted though)
     */
    @Nonnull
    public Set<RedditStory> getFrontPageStories(final int limit) throws RedditClientException {
        final LinkedHashSet<RedditStory> stories = new LinkedHashSet<>();
        String currentAfter = "";
        int lastCount = 0;
        while (stories.size() < limit) {
            final RedditListing<RedditStory> subListing;
            try {
                final JsonNode jsonNodeResponse = redditEndpoint.path("/.json")
                        .queryParam("limit", MAX_ITEMS_PER_LISTING_PAGE)
                        .queryParam("after", currentAfter)
                        .request(MediaType.APPLICATION_JSON)
                        .get(JsonNode.class);
                subListing = new RedditListing<>(jsonNodeResponse, RedditStory.class);
            } catch (@Nonnull ClientErrorException | JsonProcessingException e) {
                this.clientExceptionMeter.mark();
                throw new RedditClientException(e);
            }

            if (subListing.getChildren().size() == 0) {
                break; //no more listing!
            }

            for (int i = 0; i < subListing.getChildren().size() && stories.size() < limit; i++) {
                stories.add(subListing.getChildren().get(i));
            }

            if (stories.size() == lastCount) {
                break; //no more stories added, fail early
            }

            lastCount = stories.size();
            currentAfter = subListing.getAfter();
        }

        return stories;
    }

    /**
     * Attempts to get the current list of "preselected" front page subreddits
     *
     * @return Set of subreddits that are part
     * @throws RedditClientException
     */
    @Nonnull
    public Set<String> getDefaultFrontPageSubreddits() throws RedditClientException {
        //the trick is to get the top 300 front page stories on reddit. This should be cached
        //3 is arbitrary, the higher the more likely we'll cover all front page reddits
        final Set<RedditStory> frontPageStories = getFrontPageStories(3 * MAX_ITEMS_PER_LISTING_PAGE);
        final Set<String> topSubreddits = new HashSet<>();
        for (final RedditStory story : frontPageStories) {
            topSubreddits.add(story.getSubreddit());
        }

        return topSubreddits;
    }

    /**
     * Given (up to MAX_ITEMS_PER_LISTING_PAGE) story ids,
     * fetch the latest information about those stories in bulk and return a hashmap of (story id -> story)
     *
     * @param storyShortIds list of SHORT reddit story ids to fetch (can be max MAX_ITEMS_PER_LISTING_PAGE) size
     * @return map of story id -> story pairs, not guaranteed though to exist
     * @throws RedditClientException
     */
    @Nonnull
    public Map<String, RedditStory> getStoriesById(@Nonnull final Set<String> storyShortIds) throws RedditClientException {
        Preconditions.checkArgument(storyShortIds.size() <= MAX_ITEMS_PER_LISTING_PAGE,
                "Cannot request more than " + MAX_ITEMS_PER_LISTING_PAGE + " stories by id at a given time");
        Preconditions.checkArgument(storyShortIds.size() > 0, "Empty list of ids passed to getStoriesById");

        final Set<String> storyLongIds = new HashSet<>(storyShortIds.size());
        for (final String storyId : storyShortIds) storyLongIds.add(RedditKind.STORY.getKey() + "_" + storyId);

        final RedditListing<RedditStory> stories;
        try {
            //fetch listing of all stories
            final JsonNode jsonNodeResponse = redditEndpoint.path("/by_id/" + Joiner.on(",").join(storyLongIds) + ".json")
                    .queryParam("limit", MAX_ITEMS_PER_LISTING_PAGE)
                    .request(MediaType.APPLICATION_JSON)
                    .get(JsonNode.class);
            stories = new RedditListing<>(jsonNodeResponse, RedditStory.class);
        } catch (@Nonnull RedirectionException | ProcessingException | ClientErrorException | JsonProcessingException e) {
            this.clientExceptionMeter.mark();
            throw new RedditClientException(e);
        }

        //convert to a map
        final Map<String, RedditStory> storyMap = new LinkedHashMap<>(storyShortIds.size());
        for (final RedditStory story : stories) {
            storyMap.put(story.getId(), story);
        }

        return storyMap;
    }

    /**
     * Gets the details about a specific subreddit
     *
     * @param subredditName the subreddit name (eg 'news' or 'gaybros')
     * @return RedditSubreddit object
     * @throws RedditClientException
     */
    public RedditSubreddit getSubredditByName(@Nonnull final String subredditName) throws RedditClientException {
        try {
            final JsonNode jsonNodeResponse = redditEndpoint.path("/r/" + subredditName + "/about.json")
                    .request(MediaType.APPLICATION_JSON)
                    .get(JsonNode.class);
            return RedditThing.parseThing(jsonNodeResponse, RedditSubreddit.class);
        } catch (@Nonnull RedirectionException | ProcessingException | ClientErrorException e) {
            this.clientExceptionMeter.mark();
            throw new RedditClientException(e);
        }
    }

    /**
     * Sort style for listing requests
     */
    @SuppressWarnings("unused")
    public enum SortStyle {
        TOP("top"), CONTROVERSIAL("controversial"),
        HOT("hot"), NEW("new");

        private final String urlString;

        SortStyle(final String urlString) {
            this.urlString = urlString;
        }

        @Override
        public String toString() {
            return urlString;
        }
    }

    /**
     * Time range for /top and /controversial SortStyles - this doesn't work on any other sort styles
     */
    @SuppressWarnings("unused")
    public enum TimeRange {
        HOUR("hour"), DAY("day"), WEEK("week"),
        MONTH("month"), YEAR("year"), ALL("all");

        private final String urlString;

        TimeRange(final String urlString) {
            this.urlString = urlString;
        }

        @Override
        public String toString() {
            return urlString;
        }
    }

}