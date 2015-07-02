package com.andrewortman.reddcrawl.repository;

import com.andrewortman.reddcrawl.repository.model.SubredditHistoryModel;
import com.andrewortman.reddcrawl.repository.model.SubredditModel;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface SubredditRepository {

    /**
     * Gets a list of all subreddit names currently in the database
     *
     * @return list of subreddit names (eg news,gaybros,funny)
     */
    List<String> getAllSubredditNames();

    /**
     * Get a list of subreddits needing a history update
     * @param lastUpdateTime a date in time in which the last update time should before before to be consider update-worthy
     */
    List<SubredditModel> findSubredditsNeedingUpdate(Date lastUpdateTime);

    /**
     * Find the subreddit by it's name - like 'funny'
     *
     * @param name the subreddit name
     * @return filled in subreddit model or null if not found
     */
    SubredditModel findSubredditByName(String name);

    /**
     * Save a newly discovered subreddit
     *
     * @param subredditModel The filled in subreddit model
     * @return the persisted subreddit model
     */
    SubredditModel saveNewSubreddit(SubredditModel subredditModel);

    /**
     * Add a history item to the subreddit
     *
     * @param subredditModel The subredditmodel to add history to
     * @param historyItem    the history item
     * @return the persisted history model
     */
    SubredditHistoryModel addSubredditHistory(SubredditModel subredditModel, SubredditHistoryModel historyItem);

    /**
     * Get the first subreddit history item before a given date. If no history found, use the first history item in the db
     *
     * @param date the date to scan behind
     * @return the subreddit history model or null if no history exists for the subreddit
     */
    SubredditHistoryModel getSubredditHistoryModelFirstBeforeDate(Date date);
}
