package com.andrewortman.reddcrawl.client.models;

import com.andrewortman.reddcrawl.client.RedditClientException;
import com.andrewortman.reddcrawl.client.models.meta.RedditKind;
import com.andrewortman.reddcrawl.client.models.meta.RedditModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.Nonnull;

public abstract class RedditThing {
    //static object mapper - handles single value arrays
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    /**
     * Parses a RedditThing from a JsonNode - does validation of the thing first to make sure it is the
     * same type as annotated with the RedditModel annotation and then maps it with a static object mapper
     *
     * @param node       json node to parse into a single Reddit "Thing"
     * @param thingClass the expected class of the Reddit Thing
     * @param <T>        The expected thing type
     * @return The reddit thing
     * @throws RedditClientException if any validation mishap happens, it is thrown via RedditClientException
     */
    public static <T extends RedditThing> T parseThing(@Nonnull final JsonNode node, @Nonnull final Class<T> thingClass)
            throws RedditClientException {
        //first determine if it is the right thing
        final String kind = node.path("kind").asText();
        final RedditModel modelForThing = thingClass.getAnnotation(RedditModel.class);
        if (modelForThing == null) {
            throw new RedditClientException("Class is not annotated with a model - not sure what to do with it");
        }

        final RedditKind kindForThing = modelForThing.kind();

        if (!kind.equals(kindForThing.getKey())) {
            throw new RedditClientException("Kind `" + kind + "` does not match up with expected value " + kindForThing.getKey());
        }

        final JsonNode dataNode = node.path("data");
        if (dataNode == null) {
            throw new RedditClientException("No data node found for kind " + kind);
        }

        try {
            return OBJECT_MAPPER.treeToValue(dataNode, thingClass);
        } catch (@Nonnull final JsonProcessingException e) {
            //wrap jsonprocessingexception with ta reddit client exception
            throw new RedditClientException(e);
        }
    }

    @Nonnull
    public abstract String getFullId();
}
