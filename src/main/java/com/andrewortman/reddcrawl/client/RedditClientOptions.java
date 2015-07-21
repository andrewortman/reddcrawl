package com.andrewortman.reddcrawl.client;

import javax.annotation.Nonnull;

/**
 * reddcrawl
 * com.andrewortman.reddcrawl.client
 *
 * @author andrewo
 */
public class RedditClientOptions {

    @Nonnull
    public final String queryEndpoint;

    @Nonnull
    public final String userAgent;

    public final int readTimeout;

    public final int connectTimeout;

    public RedditClientOptions(@Nonnull String queryEndpoint, @Nonnull String userAgent, int readTimeout, int connectTimeout) {
        this.queryEndpoint = queryEndpoint;
        this.userAgent = userAgent;
        this.readTimeout = readTimeout;
        this.connectTimeout = connectTimeout;
    }

    @Nonnull
    public String getQueryEndpoint() {
        return queryEndpoint;
    }

    @Nonnull
    public String getUserAgent() {
        return userAgent;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }
}
