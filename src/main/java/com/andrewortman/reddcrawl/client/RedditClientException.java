package com.andrewortman.reddcrawl.client;

/**
 * Created by andrewortman on 6/6/15.
 */
public class RedditClientException extends Exception {
    public RedditClientException(final String message) {
        super(message);
    }

    public RedditClientException(final Throwable parent) {
        super(parent);
    }
}
