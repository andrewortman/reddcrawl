package com.andrewortman.reddcrawl.repository.model;

import javax.annotation.Nonnull;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Date;

/**
 * Holds the representation of a single entry of reddit subreddit history in the database
 */
@SuppressWarnings("NullableProblems")
@Entity(name = "subreddit_history")
public class SubredditHistoryModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Nonnull
    @ManyToOne(optional = false)
    @JoinColumn(name = "subreddit", nullable = false)
    private SubredditModel subreddit;

    @Nonnull
    @Column(name = "timestamp", nullable = false)
    private Date timestamp;

    @Column(name = "subscribers", nullable = false)
    private long subscribers;

    @Column(name = "active", nullable = false)
    private int active;

    @Column(name = "comment_hide_mins", nullable = false)
    private int commentHideMins;

    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    @Nonnull
    public SubredditModel getSubreddit() {
        return subreddit;
    }

    public void setSubreddit(@Nonnull final SubredditModel subreddit) {
        this.subreddit = subreddit;
    }

    @Nonnull
    public Date getTimestamp() {
        return new Date(timestamp.getTime());
    }

    public void setTimestamp(@Nonnull final Date timestamp) {
        this.timestamp = new Date(timestamp.getTime());
    }

    public long getSubscribers() {
        return subscribers;
    }

    public void setSubscribers(final long subscribers) {
        this.subscribers = subscribers;
    }

    public int getActive() {
        return active;
    }

    public void setActive(final int active) {
        this.active = active;
    }

    public int getCommentHideMins() {
        return commentHideMins;
    }

    public void setCommentHideMins(final int commentHideMins) {
        this.commentHideMins = commentHideMins;
    }
}
