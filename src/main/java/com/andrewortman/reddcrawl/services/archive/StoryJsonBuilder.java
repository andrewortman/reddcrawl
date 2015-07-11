package com.andrewortman.reddcrawl.services.archive;

import com.andrewortman.reddcrawl.repository.model.StoryHistoryModel;
import com.andrewortman.reddcrawl.repository.model.StoryModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StoryJsonBuilder {
    public static JsonNode renderJsonSummaryForStory(@Nonnull final StoryModel storyModel) {
        return JsonNodeFactory.instance.objectNode()
                .put("id", storyModel.getRedditShortId())
                .put("title", storyModel.getTitle())
                .put("author", storyModel.getAuthor())
                .put("createdAt", storyModel.getCreatedAt().getTime())
                .put("discoveredAt", storyModel.getDiscoveredAt().getTime())
                .put("domain", storyModel.getDomain())
                .put("url", storyModel.getUrl())
                .put("thumbnail", storyModel.getThumbnail())
                .put("permalink", storyModel.getPermalink())
                .put("score", storyModel.getScore())
                .put("comments", storyModel.getComments())
                .put("hotness", storyModel.getHotness())
                .put("gilded", storyModel.getGilded())
                .put("subreddit", storyModel.getSubreddit().getName())
                .put("isSelf", storyModel.getIsSelf())
                .put("selfText", storyModel.getSelftext())
                .put("isOver18", storyModel.getOver18())
                .put("isStickied", storyModel.getStickied())
                .put("distinguished", storyModel.getDistinguished());
    }

    public static JsonNode renderJsonDetailForStory(@Nonnull final StoryModel storyModel, @Nonnull final List<StoryHistoryModel> storyHistoryModels) {
        final ObjectNode storyNode = JsonNodeFactory.instance.objectNode();
        storyNode.set("summary", renderJsonSummaryForStory(storyModel));
        storyNode.set("history", renderJsonForStoryHistory(storyHistoryModels));

        return storyNode;
    }

    public static JsonNode renderJsonForStoryHistory(@Nonnull final List<StoryHistoryModel> historyModels) {
        final JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;
        final ObjectNode rootNode = jsonNodeFactory.objectNode();

        final ArrayNode timestampArray = jsonNodeFactory.arrayNode();
        final ArrayNode scoreArray = jsonNodeFactory.arrayNode();
        final ArrayNode hotnessArray = jsonNodeFactory.arrayNode();
        final ArrayNode commentsArray = jsonNodeFactory.arrayNode();
        final ArrayNode gildedArray = jsonNodeFactory.arrayNode();

        //sort by timestamp on the app server side (there is no index on timestamp in the db)
        Collections.sort(historyModels, new Comparator<StoryHistoryModel>() {
            @Override
            public int compare(@Nonnull final StoryHistoryModel o1, @Nonnull final StoryHistoryModel o2) {
                return o1.getTimestamp().compareTo(o2.getTimestamp());
            }
        });

        for (final StoryHistoryModel historyModel : historyModels) {
            timestampArray.add(historyModel.getTimestamp().getTime());
            scoreArray.add(historyModel.getScore());
            hotnessArray.add(historyModel.getHotness());
            gildedArray.add(historyModel.getGilded());
            commentsArray.add(historyModel.getComments());
        }

        rootNode.set("timestamp", timestampArray);
        rootNode.set("score", scoreArray);
        rootNode.set("hotness", hotnessArray);
        rootNode.set("gilded", gildedArray);
        rootNode.set("comments", commentsArray);

        return rootNode;
    }
}
