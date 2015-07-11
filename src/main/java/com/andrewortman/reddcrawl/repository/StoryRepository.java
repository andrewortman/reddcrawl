package com.andrewortman.reddcrawl.repository;

import com.andrewortman.reddcrawl.repository.model.StoryHistoryModel;
import com.andrewortman.reddcrawl.repository.model.StoryModel;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Date;
import java.util.List;

@Repository
public interface StoryRepository {
    /**
     * Find a story given a short id
     *
     * @param redditShortId the short id from reddit
     * @return StoryModel or null if not exists
     */
    @Nullable
    StoryModel findStoryByRedditShortId(@Nonnull final String redditShortId);

    /**
     * Gets a Top N list of the hottest stories being tracked
     *
     * @param limit          max number of results
     * @param fetchSubreddit fetch the subreddit information along with each story
     * @return List of storyModels, without histories preloaded
     */
    @Nonnull
    List<StoryModel> getHottestStories(int limit, boolean fetchSubreddit);

    /**
     * Finds stories that need an update
     *
     * @param earliestCreateTime create time of the oldest story to consider
     * @param lastUpdateTime     the latest time the story has been updated before
     * @param limit              the max number of results to return
     * @return a list of stories needing update
     */
    @Nonnull
    List<StoryModel> findStoriesNeedingUpdate(@Nonnull Date earliestCreateTime, @Nonnull Date lastUpdateTime, int limit);

    /**
     * Get stories that were created before a specific time
     *
     * @param latestCreateDate the latest timestamp in which the story was created
     * @param limit            max number of stories to return
     * @return list of all stories created before latestCreateDate
     */
    @Nonnull
    List<StoryModel> findArchivableStories(@Nonnull final Date latestCreateDate, int limit);

    /**
     * Removes the stories and associated data with those stories from the database. Use this after archiving to a file
     *
     * @param stories List of story models to remove
     * @return Number of stories actually removed
     */
    @Nonnull
    Integer deleteStories(@Nonnull final List<StoryModel> stories);

    /**
     * Save a newly discovered story
     *
     * @param partialStory   A StoryModel object filled in with all available metadata except that relating to the first history item
     * @param partialHistory First history element
     * @return The StoryModel that was saved
     */
    @Nonnull
    StoryModel saveNewStory(@Nonnull StoryModel partialStory, @Nonnull StoryHistoryModel partialHistory);

    /**
     * Adds a story history item to a story
     *
     * @param story       Story Model to update
     * @param historyItem History item to insert - if null, this will not create a history line item, but update the checked time
     * @return boolean if story model was updated
     */
    boolean addStoryHistory(@Nonnull StoryModel story, @Nullable StoryHistoryModel historyItem);

    /**
     * Returns an list of all associated history items for the story
     *
     * @param storyModel the story to find all the histories for
     * @return in-order (by time) list of story history items
     */
    @Nonnull
    List<StoryHistoryModel> getStoryHistory(@Nonnull StoryModel storyModel);
}
