package com.andrewortman.reddcrawl.client.authentication;

import javax.ws.rs.client.ClientRequestContext;
import java.io.IOException;

/**
 * reddcrawl
 * com.andrewortman.reddcrawl.client.authentication
 *
 * @author andrewo
 */
public class NoopAuthenticatingRequestFilter implements AuthenticatingRequestFilter {
    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        //do nothing
    }
}
