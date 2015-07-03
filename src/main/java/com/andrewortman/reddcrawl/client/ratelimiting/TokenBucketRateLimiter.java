package com.andrewortman.reddcrawl.client.ratelimiting;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Uses a Token Bucket to perform rate limiting. This makes reddit.com happy - it will
 * only allow a certain number of requests within a minute. If the current "bucket"
 * of requests is empty, it forces you to delay depending on the deficit. If the "bucket"
 * though has more than one token available, then you don't have to delay at all.
 * <p/>
 * This allows burstiness if something slowed down on our end so we don't waste our valuable
 * requests
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    @Nonnull
    private final Integer maxRequestsPerMinute;

    @Nonnull
    private Integer currentTokenCount = 0;

    @Nonnull
    private DateTime lastTokenInsertTime = DateTime.now();

    public TokenBucketRateLimiter(@Nonnull final Integer maxRequestsPerMinute,
                                  @Nonnull final MetricRegistry metricRegistry) {
        lastTokenInsertTime = DateTime.now();
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.currentTokenCount = maxRequestsPerMinute; //fill up the bucket at first

        //register a gauge that monitors the current token count
        metricRegistry.register(MetricRegistry.name("reddcrawl", "client", "tokens"),
                new Gauge<Integer>() {
                    @Override
                    public Integer getValue() {
                        return currentTokenCount;
                    }
                });
    }

    @Override
    public Long getAmountOfTimeToSleep() {
        synchronized (this) {
            logger.debug("get amount of time requested from thread {}", Thread.currentThread().getName());
            final DateTime currentTime = DateTime.now();
            final long millisSinceLastInsert = currentTime.getMillis() - lastTokenInsertTime.getMillis();
            final long millisBetweenRequests = 60000L / maxRequestsPerMinute;

            final int numTokensToAdd = (int) (millisSinceLastInsert / millisBetweenRequests);
            logger.debug("Need to add {} tokens to the pool", numTokensToAdd);

            //update current token count and increment the last token insert time appropriately
            currentTokenCount += numTokensToAdd;
            lastTokenInsertTime = new DateTime(lastTokenInsertTime.getMillis() + millisBetweenRequests * numTokensToAdd);

            //cap out max token count to maxRequestsPerMinute
            if (currentTokenCount > maxRequestsPerMinute) {
                currentTokenCount = maxRequestsPerMinute;
            }

            logger.debug("token pool is now {} tokens", currentTokenCount);

            if (currentTokenCount > 0) {
                currentTokenCount = currentTokenCount - 1;
                logger.debug("no need to wait, we have at least one token. current token count is now {} ", currentTokenCount);
                return 0L; //no need to wait, we have at least one token
            } else {
                currentTokenCount = currentTokenCount - 1;
                final long deficitAmount = Math.abs(currentTokenCount);
                final long timeToWait = deficitAmount * millisBetweenRequests;
                logger.debug("need to wait, we have a deficit of {} tokens now, so we need to wait {} ms", deficitAmount, timeToWait);
                return timeToWait;
            }
        }
    }
}
