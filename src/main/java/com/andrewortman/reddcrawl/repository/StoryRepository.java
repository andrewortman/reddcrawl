package com.andrewortman.reddcrawl.repository;

import com.andrewortman.reddcrawl.repository.model.StoryHistoryModel;
import com.andrewortman.reddcrawl.repository.model.StoryModel;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface StoryRepository {
    /**
     * Find a story given a short id
     *
     * @param redditShortId    the short id from reddit
     * @param includeHistories whether or not to fetch histories with the story
     * @return StoryModel or null if not exists
     */
    public StoryModel findStoryByRedditShortId(final String redditShortId, final boolean includeHistories);

    /**
     * Gets a Top N list of the hottest stories being tracked
     *
     * @param limit max number of results
     * @return List of storyModels, without histories preloaded
     */
    List<StoryModel> getHottestStories(Integer limit);

    /**
     * Gets a Top N list of the hottest stories being tracked with subreddit information joined in eagerly
     *
     * @param limit max number of results
     * @return List of storyModels, without histories preloaded
     */
    List<StoryModel> getHottestStoriesWithSubreddit(Integer limit);

    /**
     * Finds stories that need an update
     *
     * @param earliestCreateTime create time of the oldest story to consider
     * @param lastUpdateTime     the latest time the story has been updated before
     * @param limit              the max number of results to return
     * @return a list of stories needing update
     */
    List<StoryModel> findStoriesNeedingUpdate(Date earliestCreateTime, Date lastUpdateTime, int limit);

    /**
     * Save a newly discovered story
     *
     * @param partialStory   A StoryModel object filled in with all available metadata except that relating to the first history item
     * @param partialHistory First history element
     * @return The StoryModel that was saved
     */
    StoryModel saveNewStory(StoryModel partialStory, StoryHistoryModel partialHistory);

    /**
     * Adds a story history item to a story
     *
     * @param story       Story Model to update
     * @param historyItem History item to insert
     * @return boolean if story model was updated
     */
    boolean addStoryHistory(StoryModel story, StoryHistoryModel historyItem);

    /**
     * Returns an list of all associated history items for the story
     *
     * @param storyModel the story to find all the histories for
     * @return in-order (by time) list of story history items
     */
    List<StoryHistoryModel> getStoryHistory(StoryModel storyModel);
}
