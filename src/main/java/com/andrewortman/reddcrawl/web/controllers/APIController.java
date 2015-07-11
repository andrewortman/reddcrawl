package com.andrewortman.reddcrawl.web.controllers;

import com.andrewortman.reddcrawl.repository.StoryRepository;
import com.andrewortman.reddcrawl.repository.model.StoryHistoryModel;
import com.andrewortman.reddcrawl.repository.model.StoryModel;
import com.andrewortman.reddcrawl.services.archive.StoryJsonBuilder;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Nonnull;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Hosts all of the REST API endpoints the frontend uses to render the current state of the word
 */

@Controller
public class APIController {

    @Nonnull
    private final StoryRepository storyRepository;

    @Nonnull
    private final StoryJsonBuilder storyJsonBuilder;

    @Autowired
    public APIController(@Nonnull final StoryRepository storyRepository,
                         @Nonnull final StoryJsonBuilder storyJsonBuilder) {
        this.storyRepository = storyRepository;
        this.storyJsonBuilder = storyJsonBuilder;
    }

    @RequestMapping(value = "/story/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    @ResponseBody
    public ResponseEntity getStory(@PathVariable("id") @Nonnull final String storyId) {
        final StoryModel storyModel = storyRepository.findStoryByRedditShortId(storyId);
        if (storyModel == null) {
            return ResponseEntity.notFound().build();
        }

        final List<StoryHistoryModel> storyHistoryModels = storyRepository.getStoryHistory(storyModel);

        return ResponseEntity.ok(StoryJsonBuilder.renderJsonDetailForStory(storyModel, storyHistoryModels));
    }

    @RequestMapping(value = "/stories", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    @ResponseBody
    public ResponseEntity getTopStories() {
        final ArrayNode storiesList = JsonNodeFactory.instance.arrayNode();
        final List<StoryModel> hottestStories = storyRepository.getHottestStories(100, true);

        for (final StoryModel storyModel : hottestStories) {
            storiesList.add(StoryJsonBuilder.renderJsonSummaryForStory(storyModel));
        }

        return ResponseEntity.ok(storiesList);
    }
}
