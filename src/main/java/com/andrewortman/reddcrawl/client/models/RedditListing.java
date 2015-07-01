package com.andrewortman.reddcrawl.client.models;

import com.andrewortman.reddcrawl.client.RedditClientException;
import com.andrewortman.reddcrawl.client.models.meta.RedditKind;
import com.andrewortman.reddcrawl.client.models.meta.RedditModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@RedditModel(kind = RedditKind.LISTING)
public class RedditListing<T extends RedditThing> extends RedditThing implements Iterable<T> {

    @Nonnull
    private final String before;

    @Nonnull
    private final String after;

    @Nonnull
    private final String modhash;

    @Nonnull
    private final List<T> children;

    public RedditListing(@Nonnull final JsonNode rootNode,
                         @Nonnull final Class<T> thingClass) throws RedditClientException, JsonProcessingException {
        if (!rootNode.path("kind").asText().equals(RedditKind.LISTING.getKey())) {
            throw new RedditClientException("Cannot parse Listing because it's type was not 'Listing'");
        }

        final JsonNode dataNode = rootNode.path("data");
        if (dataNode == null) {
            throw new RedditClientException("Cannot parse Listing because it doesn't have a data node");
        }

        this.before = dataNode.path("before").asText("");
        this.after = dataNode.path("after").asText("");
        this.modhash = dataNode.path("before").asText();

        final JsonNode arrayNode = dataNode.path("children");
        this.children = new ArrayList<T>(arrayNode.size());
        for (final JsonNode node : arrayNode) {
            this.children.add(RedditThing.parseThing(node, thingClass));
        }
    }

    public String getBefore() {
        return before;
    }

    public String getAfter() {
        return after;
    }

    public String getModhash() {
        return modhash;
    }

    public List<T> getChildren() {
        return children;
    }

    @Override
    public Iterator<T> iterator() {
        return children.iterator();
    }

}
