package com.andrewortman.reddcrawl.client.ratelimiting;

/**
 * Utility class that does not rate limit at all - useful for testing
 */
public class NoopRateLimiter implements RateLimiter {
    @Override
    public Long getAmountOfTimeToSleep() {
        return 0L;
    }
}
