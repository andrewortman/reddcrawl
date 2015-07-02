package com.andrewortman.reddcrawl.repository;

import com.andrewortman.reddcrawl.repository.model.StoryHistoryModel;
import com.andrewortman.reddcrawl.repository.model.StoryModel;
import org.springframework.stereotype.Component;

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
    public StoryModel findStoryByRedditShortId(final String redditShortId, final boolean includeHistories) {
        final String query = "SELECT s from story s "
                + (includeHistories ? "left join fetch s.history" : "") +
                " where s.redditShortId = :redditShortId";

        try {
            return entityManager.createQuery(query, StoryModel.class)
                    .setParameter("redditShortId", redditShortId)
                    .getSingleResult();
        } catch (final NoResultException ignored) {
            return null;
        }
    }

    @Override
    public List<StoryModel> getHottestStories(final Integer limit) {
        return entityManager.createQuery("SELECT s FROM story s order by s.hotness desc", StoryModel.class)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public List<StoryModel> getHottestStoriesWithSubreddit(final Integer limit) {
        return entityManager.createQuery("SELECT s FROM story s left join fetch s.subreddit as subreddit order by s.hotness desc", StoryModel.class)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    @Transactional
    public StoryModel saveNewStory(final StoryModel partialStory, final StoryHistoryModel partialHistory) {
        try {
            final StoryModel foundStory = findStoryByRedditShortId(partialStory.getRedditShortId(), false);
            if (foundStory != null) {
                return foundStory;
            }
        } catch (final NoResultException ignored) {
            //ignored - this means a story should be saved
        }

        partialStory.setDiscoveredAt(partialHistory.getTimestamp());
        partialStory.setUpdatedAt(partialHistory.getTimestamp());
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
    public boolean addStoryHistory(final StoryModel storyModel, final StoryHistoryModel historyItem) {
        //do a directed update
        final int numRows = entityManager.createQuery("UPDATE story s set " +
                "s.updatedAt=current_timestamp, s.hotness=:hotness, s.score=:score, s.comments=:comments, s.gilded=:gilded " +
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

        return false;
    }

    @Override
    public List<StoryModel> findStoriesNeedingUpdate(final Date earliestCreateTime, final Date lastUpdateTime, final int limit) {
        return entityManager.createQuery("SELECT s FROM story s WHERE s.updatedAt < :lastUpdateTime and s.discoveredAt > :earliestCreateTime ORDER BY s.hotness DESC", StoryModel.class)
                .setParameter("lastUpdateTime", lastUpdateTime)
                .setParameter("earliestCreateTime", earliestCreateTime)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public List<StoryHistoryModel> getStoryHistory(final StoryModel storyModel) {
        return entityManager.createQuery("SELECT h FROM story_history h WHERE h.story = :story ORDER BY h.timestamp ASC", StoryHistoryModel.class)
                .setParameter("story", storyModel)
                .getResultList();
    }
}
