package com.andrewortman.reddcrawl.client.models.meta;

public enum RedditKind {
    STORY("t3"),
    SUBREDDIT("t5"),
    LISTING("Listing");

    private final String key;

    RedditKind(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
