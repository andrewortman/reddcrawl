package com.andrewortman.reddcrawl.client;

import com.andrewortman.reddcrawl.client.ratelimiting.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.IOException;

/**
 * Jersey client filter that will delay a request from going through if the rate limiter tells it to
 */
public class RateLimitingClientRequestFilter implements ClientRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitingClientRequestFilter.class);

    @Nonnull
    private final RateLimiter limiter;

    public RateLimitingClientRequestFilter(@Nonnull final RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public void filter(final ClientRequestContext requestContext) throws IOException {
        final long msToWait = limiter.getAmountOfTimeToSleep();
        if (msToWait > 0) {
            try {
                LOGGER.debug("RateLimiter told me to wait {} ms. Thread sleeping", msToWait);
                Thread.sleep(msToWait);
            } catch (@Nonnull final InterruptedException e) {
                LOGGER.warn("bailing out before request - received InterruptedException");
                throw new IOException(e);
            }
        }
    }
}
