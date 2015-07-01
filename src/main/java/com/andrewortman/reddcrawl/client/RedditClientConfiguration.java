package com.andrewortman.reddcrawl.client;

import com.andrewortman.reddcrawl.ReddcrawlCommonConfiguration;
import com.andrewortman.reddcrawl.client.ratelimiting.RateLimiter;
import com.andrewortman.reddcrawl.client.ratelimiting.TokenBucketRateLimiter;
import com.codahale.metrics.MetricRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import javax.annotation.Nonnull;

@Configuration
@Import(ReddcrawlCommonConfiguration.class)
public class RedditClientConfiguration {

    @Autowired
    private Environment environment;

    @Autowired
    private MetricRegistry metricsRegistry;

    @Bean
    public RateLimiter tokenBucketRateLimiter() {
        return new TokenBucketRateLimiter(environment.getRequiredProperty("client.rpm", Integer.class), metricsRegistry);
    }

    @Bean
    public RedditClient redditClient(@Nonnull final RateLimiter rateLimiter) {
        return new RedditClient(environment.getRequiredProperty("client.useragent"),
                environment.getRequiredProperty("client.timeout.connect", Integer.class),
                environment.getRequiredProperty("client.timeout.read", Integer.class),
                environment.getRequiredProperty("client.timeout.baseuri"),
                rateLimiter,
                metricsRegistry);
    }
}
