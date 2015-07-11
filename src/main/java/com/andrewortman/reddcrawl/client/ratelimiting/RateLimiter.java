package com.andrewortman.reddcrawl.client.ratelimiting;

/**
 * A RateLimiter is used by the RedditClient to slow down requests to make
 * sure we obey reddit's API policy.
 */
public interface RateLimiter {
    long getAmountOfTimeToSleep();
}
