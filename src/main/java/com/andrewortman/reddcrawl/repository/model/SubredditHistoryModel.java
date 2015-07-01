package com.andrewortman.reddcrawl.repository.model;

import com.andrewortman.reddcrawl.repository.Views;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

import javax.annotation.Nonnull;
import javax.persistence.*;
import java.util.Date;

/**
 * Holds the representation of a single entry of reddit subreddit history in the database
 */
@Entity(name = "subreddit_history")
public class SubredditHistoryModel {
    @Id
    @Nonnull
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(Views.Short.class)
    private Long id;

    @Nonnull
    @ManyToOne(optional = false)
    @JoinColumn(name = "subreddit", nullable = false)
    @JsonIgnore
    private SubredditModel subreddit;

    @Nonnull
    @Column(name = "timestamp", nullable = false)
    @JsonView(Views.Short.class)
    private Date timestamp;

    @Nonnull
    @Column(name = "subscribers", nullable = false)
    @JsonView(Views.Short.class)
    private Long subscribers;

    @Nonnull
    @Column(name = "active", nullable = false)
    @JsonView(Views.Short.class)
    private Integer active;

    @Nonnull
    @Column(name = "comment_hide_mins", nullable = false)
    @JsonView(Views.Short.class)
    private Integer commentHideMins;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public SubredditModel getSubreddit() {
        return subreddit;
    }

    public void setSubreddit(final SubredditModel subreddit) {
        this.subreddit = subreddit;
    }

    public Date getTimestamp() {
        return new Date(timestamp.getTime());
    }

    public void setTimestamp(final Date timestamp) {
        this.timestamp = new Date(timestamp.getTime());
    }

    public Long getSubscribers() {
        return subscribers;
    }

    public void setSubscribers(final Long subscribers) {
        this.subscribers = subscribers;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(final Integer active) {
        this.active = active;
    }

    public Integer getCommentHideMins() {
        return commentHideMins;
    }

    public void setCommentHideMins(final Integer commentHideMins) {
        this.commentHideMins = commentHideMins;
    }
}
