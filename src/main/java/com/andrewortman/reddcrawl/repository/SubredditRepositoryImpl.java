package com.andrewortman.reddcrawl.repository;

import com.andrewortman.reddcrawl.repository.model.SubredditHistoryModel;
import com.andrewortman.reddcrawl.repository.model.SubredditModel;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.Date;
import java.util.List;

@Repository
public class SubredditRepositoryImpl implements SubredditRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Nullable
    public SubredditModel findSubredditByName(@Nonnull final String name) {
        try {
            return entityManager.createQuery("SELECT s from subreddit s where s.name = :name", SubredditModel.class)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (@Nonnull final NoResultException ignored) {
            return null;
        }
    }

    @Override
    @Nonnull
    public List<SubredditModel> getAllRecentlySeenSubreddits(@Nonnull final Date lastSeenTime) {
        return entityManager
                .createQuery("SELECT s from subreddit s where s.seenAt > :lastSeenTime", SubredditModel.class)
                .setParameter("lastSeenTime", lastSeenTime)
                .getResultList();
    }

    @Override
    @Nonnull
    public List<SubredditModel> findSubredditsNeedingUpdate(@Nonnull final Date lastUpdateTime) {
        return entityManager
                .createQuery("SELECT s from subreddit s where s.updatedAt < :lastUpdateTime", SubredditModel.class)
                .setParameter("lastUpdateTime", lastUpdateTime)
                .getResultList();
    }

    @Override
    @Nonnull
    @Transactional
    public SubredditModel saveNewSubreddit(@Nonnull final SubredditModel subredditModel) {
        subredditModel.setCreatedAt(new Date());
        subredditModel.setUpdatedAt(new Date());
        subredditModel.setSeenAt(new Date());
        entityManager.persist(subredditModel);
        return subredditModel;
    }

    @Override
    @Nonnull
    @Transactional
    public SubredditHistoryModel addSubredditHistory(@Nonnull final SubredditModel subredditModel, @Nonnull final SubredditHistoryModel historyItem) {
        final SubredditModel managedSubredditModel = entityManager.merge(subredditModel);
        managedSubredditModel.setUpdatedAt(new Date());

        historyItem.setTimestamp(new Date());
        historyItem.setSubreddit(managedSubredditModel);
        entityManager.persist(historyItem);

        return historyItem;
    }

    @Override
    @Nullable
    public SubredditHistoryModel getSubredditHistoryModelFirstBeforeDate(@Nonnull final Date date) {
        final SubredditHistoryModel firstBeforeDate =
                entityManager.createQuery("SELECT h from subreddit_history where timestamp < :date AND subreddit = :subreddit", SubredditHistoryModel.class)
                        .getSingleResult();

        if (firstBeforeDate != null) {
            return firstBeforeDate;
        } else {
            //if there was no history before the date, try to select the first one of all time instead
            try {
                return entityManager.createQuery("SELECT h from subreddit_history order by timestamp asc", SubredditHistoryModel.class)
                        .getSingleResult();
            } catch (@Nonnull final NoResultException ignored) {
                return null;
            }
        }
    }

    @Override
    @Transactional
    public boolean markSubredditAsSeen(@Nonnull final SubredditModel subredditModel) {
        final int numRows = entityManager.createQuery("update subreddit s set s.seenAt = current_timestamp  where id=:id")
                .setParameter("id", subredditModel.getId())
                .executeUpdate();

        return numRows > 0;
    }
}
