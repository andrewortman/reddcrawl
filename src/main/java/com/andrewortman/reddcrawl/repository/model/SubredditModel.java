package com.andrewortman.reddcrawl.repository.model;

import javax.annotation.Nonnull;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.util.Date;

/**
 * Holds the representation of a reddit subreddit in the database
 */
@SuppressWarnings("NullableProblems")
@Entity(name = "subreddit")
public class SubredditModel {
    @Nonnull
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Nonnull
    @Column(name = "reddit_short_id", nullable = false)
    private String subredditShortId;

    @Nonnull
    @Column(name = "name", nullable = false)
    private String name;

    @Nonnull
    @Column(name = "title", nullable = false)
    private String title;

    @Nonnull
    @Column(name = "url", nullable = false)
    private String url;

    @Nonnull
    @Column(name = "summary", nullable = false)
    private String summary;

    @Nonnull
    @Column(name = "description", nullable = false)
    private String description;

    @Nonnull
    @Column(name = "submission_type", nullable = false)
    private String submissionType;

    @Nonnull
    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Nonnull
    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;

    @Nonnull
    @Column(name = "seen_at", nullable = false)
    private Date seenAt;

    public int getId() {
        return id;
    }

    public void setId(final int id) {
        this.id = id;
    }

    @Nonnull
    public String getSubredditShortId() {
        return subredditShortId;
    }

    public void setSubredditShortId(@Nonnull final String subredditShortId) {
        this.subredditShortId = subredditShortId;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public void setName(@Nonnull final String name) {
        this.name = name;
    }

    @Nonnull
    public String getTitle() {
        return title;
    }

    public void setTitle(@Nonnull final String title) {
        this.title = title;
    }

    @Nonnull
    public String getUrl() {
        return url;
    }

    public void setUrl(@Nonnull final String url) {
        this.url = url;
    }

    @Nonnull
    public String getSummary() {
        return summary;
    }

    public void setSummary(@Nonnull final String summary) {
        this.summary = summary;
    }

    @Nonnull
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nonnull final String description) {
        this.description = description;
    }

    @Nonnull
    public String getSubmissionType() {
        return submissionType;
    }

    public void setSubmissionType(@Nonnull final String submissionType) {
        this.submissionType = submissionType;
    }

    @Nonnull
    public Date getCreatedAt() {
        return new Date(createdAt.getTime());
    }

    public void setCreatedAt(@Nonnull final Date createdAt) {
        this.createdAt = new Date(createdAt.getTime());
    }

    @Nonnull
    public Date getUpdatedAt() {
        return new Date(updatedAt.getTime());
    }

    public void setUpdatedAt(@Nonnull final Date updatedAt) {
        this.updatedAt = new Date(updatedAt.getTime());
    }

    @Nonnull
    public Date getSeenAt() {
        return new Date(seenAt.getTime());
    }

    public void setSeenAt(@Nonnull final Date seenAt) {
        this.seenAt = new Date(seenAt.getTime());
    }
}
