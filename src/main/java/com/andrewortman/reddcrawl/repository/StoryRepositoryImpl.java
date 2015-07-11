package com.andrewortman.reddcrawl.repository;

import com.andrewortman.reddcrawl.repository.model.StoryHistoryModel;
import com.andrewortman.reddcrawl.repository.model.StoryModel;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.Date;
import java.util.List;

@Component
public class StoryRepositoryImpl implements StoryRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Nullable
    public StoryModel findStoryByRedditShortId(@Nonnull final String redditShortId) {
        try {
            return entityManager.createQuery("SELECT s from story s where s.redditShortId = :redditShortId", StoryModel.class)
                    .setParameter("redditShortId", redditShortId)
                    .getSingleResult();
        } catch (@Nonnull final NoResultException ignored) {
            return null;
        }
    }

    @Override
    @Nonnull
    public List<StoryModel> getHottestStories(final int limit, final boolean fetchSubreddit) {
        final String query = "SELECT s FROM story s " + (fetchSubreddit ? "left join fetch s.subreddit as subreddit" : "") + " order by s.hotness desc";
        return entityManager.createQuery(query, StoryModel.class)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    @Nonnull
    @Transactional
    public StoryModel saveNewStory(@Nonnull final StoryModel partialStory, @Nonnull final StoryHistoryModel partialHistory) {
        try {
            final StoryModel foundStory = findStoryByRedditShortId(partialStory.getRedditShortId());
            if (foundStory != null) {
                return foundStory;
            }
        } catch (@Nonnull final NoResultException ignored) {
            //ignored - this means a story should be saved
        }

        partialStory.setDiscoveredAt(partialHistory.getTimestamp());
        partialStory.setUpdatedAt(partialHistory.getTimestamp());
        partialStory.setCheckedAt(partialHistory.getTimestamp());
        partialStory.setHotness(partialHistory.getHotness());
        partialStory.setScore(partialHistory.getScore());
        partialStory.setComments(partialHistory.getComments());
        partialStory.setGilded(partialHistory.getGilded());
        final StoryModel managedStory = entityManager.merge(partialStory);

        partialHistory.setStory(managedStory);
        entityManager.persist(partialHistory);

        return managedStory;
    }

    @Override
    @Transactional
    public boolean addStoryHistory(@Nonnull final StoryModel storyModel,
                                   @Nullable final StoryHistoryModel historyItem) {
        if (historyItem != null) {
            //the history item exists
            final int numRows = entityManager.createQuery("UPDATE story s set " +
                    "s.updatedAt=current_timestamp, s.checkedAt=current_timestamp, s.hotness=:hotness, s.score=:score, s.comments=:comments, s.gilded=:gilded " +
                    "where s.id=:id")
                    .setParameter("hotness", historyItem.getHotness())
                    .setParameter("score", historyItem.getScore())
                    .setParameter("comments", historyItem.getComments())
                    .setParameter("gilded", historyItem.getGilded())
                    .setParameter("id", storyModel.getId())
                    .executeUpdate();

            if (numRows > 0) {
                historyItem.setStory(storyModel);
                entityManager.persist(historyItem);
                return true;
            }
        } else {
            //history item was null (no history item was returned by reddit, but we should still mark it as checked)
            final int numRows = entityManager.createQuery("UPDATE story s set s.checkedAt=current_timestamp where s.id=:id")
                    .setParameter("id", storyModel.getId())
                    .executeUpdate();

            return numRows > 0;
        }

        return false;
    }

    @Override
    @Nonnull
    public List<StoryModel> findStoriesNeedingUpdate(@Nonnull final Date earliestCreateTime,
                                                     @Nonnull final Date lastCheckTime,
                                                     final int limit) {
        return entityManager.createQuery("SELECT s FROM story s WHERE s.checkedAt <= :lastUpdateTime and s.discoveredAt >= :earliestCreateTime ORDER BY s.hotness DESC", StoryModel.class)
                .setParameter("lastUpdateTime", lastCheckTime)
                .setParameter("earliestCreateTime", earliestCreateTime)
                .setMaxResults(limit)
                .getResultList();
    }

    @Nonnull
    @Override
    public List<StoryModel> findArchivableStories(@Nonnull final Date latestCreateDate, final int limit) {
        return entityManager.createQuery("SELECT s from story s WHERE s.createdAt <= :latestCreateDate", StoryModel.class)
                .setParameter("latestCreateDate", latestCreateDate)
                .setMaxResults(limit)
                .getResultList();
    }

    @Nonnull
    @Override
    @Transactional
    public Integer deleteStories(@Nonnull final List<StoryModel> stories) {
        return entityManager.createQuery("DELETE from story s where s in :stories")
                .setParameter("stories", stories)
                .executeUpdate();
    }

    @Override
    @Nonnull
    public List<StoryHistoryModel> getStoryHistory(@Nonnull final StoryModel storyModel) {
        return entityManager.createQuery("SELECT h FROM story_history h WHERE h.story = :story ORDER BY h.timestamp ASC", StoryHistoryModel.class)
                .setParameter("story", storyModel)
                .getResultList();
    }
}
