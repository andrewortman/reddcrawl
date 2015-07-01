import com.andrewortman.reddcrawl.client.RedditClient;
import com.andrewortman.reddcrawl.client.RedditClientException;
import com.andrewortman.reddcrawl.client.models.RedditStory;
import com.andrewortman.reddcrawl.client.models.RedditSubreddit;
import com.andrewortman.reddcrawl.client.ratelimiting.NoopRateLimiter;
import com.codahale.metrics.MetricRegistry;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.*;


public class RedditClientTest {

    private static final RedditClient client = new RedditClient("reddcrawl-test", 5000, 5000, "http://reddit.com", new NoopRateLimiter(), new MetricRegistry());

    @Test
    public void testGetFrontPageSubreddits() throws RedditClientException {
        //there should be at least 45 subreddits (can burst to 50, depending on the day)
        assertTrue(client.getDefaultFrontPageSubreddits().size() >= 48);
    }

    @Test
    public void testGetFrontPageListing() throws RedditClientException {
        Set<RedditStory> stories;

        stories = client.getFrontPageStories(50);
        assertEquals(50, stories.size());

        stories = client.getFrontPageStories(200);
        assertEquals(200, stories.size());

        stories = client.getFrontPageStories(250);
        assertEquals(250, stories.size());
    }

    @Test
    public void getSubredditListing() throws RedditClientException {
        final Set<String> subreddits = new HashSet<>();
        subreddits.add("videos");
        subreddits.add("news");
        subreddits.add("funny");
        subreddits.add("gaybros");
        subreddits.add("austin");

        Set<RedditStory> stories;

        stories = client.getStoryListingForSubreddits(subreddits, RedditClient.SortStyle.HOT, RedditClient.TimeRange.DAY, 50);
        assertEquals(50, stories.size());

        stories = client.getStoryListingForSubreddits(subreddits, RedditClient.SortStyle.HOT, RedditClient.TimeRange.DAY, 200);
        assertEquals(200, stories.size());

        stories = client.getStoryListingForSubreddits(subreddits, RedditClient.SortStyle.HOT, RedditClient.TimeRange.DAY, 250);
        assertEquals(250, stories.size());
    }

    @Test
    public void getStoriesById() throws RedditClientException {

        final Set<RedditStory> stories = client.getFrontPageStories(50);
        final Set<String> storyIds = new HashSet<>();
        for (final RedditStory story : stories) storyIds.add(story.getId());

        final Map<String, RedditStory> storyMap = client.getStoriesById(storyIds);
        for (final String storyId : storyIds) {
            assertTrue(storyMap.containsKey(storyId));
            assertNotNull(storyMap.get(storyId));
        }
    }

    @Test
    public void getSubredditByName() throws RedditClientException {
        final RedditSubreddit subreddit = client.getSubredditByName("gaybros");
        assertEquals("gaybros", subreddit.getName());
    }
}
