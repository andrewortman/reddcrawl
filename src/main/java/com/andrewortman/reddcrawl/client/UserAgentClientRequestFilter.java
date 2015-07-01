package com.andrewortman.reddcrawl.client;

import javax.annotation.Nonnull;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.IOException;

/**
 * Jersey client filter that will inject our user agent into the requests
 */
public class UserAgentClientRequestFilter implements ClientRequestFilter {

    @Nonnull
    private final String userAgent;

    public UserAgentClientRequestFilter(@Nonnull final String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public void filter(@Nonnull final ClientRequestContext requestContext) throws IOException {
        requestContext.getHeaders().add("User-Agent", this.userAgent);
    }
}
