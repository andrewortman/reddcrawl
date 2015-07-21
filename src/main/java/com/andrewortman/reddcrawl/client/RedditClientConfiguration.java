package com.andrewortman.reddcrawl.client;

import com.andrewortman.reddcrawl.ReddcrawlCommonConfiguration;
import com.andrewortman.reddcrawl.client.authentication.AuthenticatingRequestFilter;
import com.andrewortman.reddcrawl.client.authentication.OauthAuthenticatingRequestFilter;
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
    @Nonnull
    public RateLimiter tokenBucketRateLimiter() {
        return new TokenBucketRateLimiter(environment.getRequiredProperty("client.rpm", Integer.class), metricsRegistry);
    }

    @Bean
    @Nonnull
    public AuthenticatingRequestFilter authenticatingRequestFilter() {
        final OauthAuthenticatingRequestFilter.OauthOptions oauthOptions = new OauthAuthenticatingRequestFilter.OauthOptions(
                environment.getRequiredProperty("client.oauth.endpoint"),
                environment.getRequiredProperty("client.oauth.clientid"),
                environment.getRequiredProperty("client.oauth.clientsecret"),
                environment.getRequiredProperty("client.oauth.username"),
                environment.getRequiredProperty("client.oauth.password")
        );

        return new OauthAuthenticatingRequestFilter(oauthOptions, environment.getRequiredProperty("client.useragent"));
    }

    @Bean
    @Nonnull
    public RedditClient redditClient(@Nonnull final RateLimiter rateLimiter,
                                     @Nonnull final AuthenticatingRequestFilter authenticatingRequestFilter) {
        final RedditClientOptions options = new RedditClientOptions(
                environment.getRequiredProperty("client.endpoint"),
                environment.getRequiredProperty("client.useragent"),
                environment.getRequiredProperty("client.timeout.read", Integer.class),
                environment.getRequiredProperty("client.timeout.connect", Integer.class)
        );

        return new RedditClient(options, rateLimiter, authenticatingRequestFilter, metricsRegistry);
    }
}
