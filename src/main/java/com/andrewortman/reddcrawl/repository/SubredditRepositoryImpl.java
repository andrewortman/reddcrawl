package com.andrewortman.reddcrawl.repository;

import com.andrewortman.reddcrawl.repository.model.SubredditHistoryModel;
import com.andrewortman.reddcrawl.repository.model.SubredditModel;
import org.springframework.stereotype.Repository;

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
    public SubredditModel findSubredditByName(final String name) {
        try {
            return entityManager.createQuery("SELECT s from subreddit s where s.name = :name", SubredditModel.class)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (final NoResultException ignored) {
            return null;
        }
    }

    @Override
    public List<String> getAllSubredditNames() {
        return entityManager
                .createQuery("SELECT s.name from subreddit s order by s.updatedAt desc", String.class)
                .getResultList();
    }

    @Override
    public List<SubredditModel> findSubredditsNeedingUpdate(final Date lastUpdateTime) {
        return entityManager
                .createQuery("SELECT s from subreddit s where s.updatedAt < :lastUpdateTime", SubredditModel.class)
                .setParameter("lastUpdateTime", lastUpdateTime)
                .getResultList();
    }

    @Override
    @Transactional
    public SubredditModel saveNewSubreddit(final SubredditModel subredditModel) {
        subredditModel.setCreatedAt(new Date());
        subredditModel.setUpdatedAt(new Date());
        entityManager.persist(subredditModel);
        return subredditModel;
    }

    @Override
    @Transactional
    public SubredditHistoryModel addSubredditHistory(final SubredditModel subredditModel, final SubredditHistoryModel historyItem) {
        final SubredditModel managedSubredditModel = entityManager.merge(subredditModel);
        managedSubredditModel.setUpdatedAt(new Date());

        historyItem.setTimestamp(new Date());
        historyItem.setSubreddit(managedSubredditModel);
        entityManager.persist(historyItem);

        return historyItem;
    }

    @Override
    public SubredditHistoryModel getSubredditHistoryModelFirstBeforeDate(final Date date) {
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
            } catch (final NoResultException ignored) {
                return null;
            }
        }
    }
}
