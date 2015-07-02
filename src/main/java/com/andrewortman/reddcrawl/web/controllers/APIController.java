package com.andrewortman.reddcrawl.web.controllers;

import com.andrewortman.reddcrawl.repository.StoryRepository;
import com.andrewortman.reddcrawl.repository.Views;
import com.andrewortman.reddcrawl.repository.model.StoryModel;
import com.fasterxml.jackson.annotation.JsonView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Nonnull;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Hosts all of the REST API endpoints the frontend uses to render the current state of the word
 */

@RestController
public class APIController {

    @Nonnull
    private final StoryRepository storyRepository;

    @Autowired
    public APIController(@Nonnull final StoryRepository storyRepository) {
        this.storyRepository = storyRepository;
    }

    @RequestMapping(value = "/story/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    @JsonView(Views.Detailed.class)
    public StoryModel getStory(@PathVariable("id") @Nonnull final String storyId) {
        return storyRepository.findStoryByRedditShortId(storyId, true);
    }

    @RequestMapping(value = "/stories", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    @JsonView(Views.Short.class)
    public List<StoryModel> getTopStories() {
        return storyRepository.getHottestStoriesWithSubreddit(100);
    }
}
