package com.andrewortman.reddcrawl.repository.model;

import com.andrewortman.reddcrawl.repository.Views;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

import javax.annotation.Nonnull;
import javax.persistence.*;
import java.util.Date;

/**
 * Holds the representation of a reddit subreddit in the database
 */
@Entity(name = "subreddit")
public class SubredditModel {
    @Nonnull
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Integer id;

    @Nonnull
    @Column(name = "reddit_short_id", nullable = false)
    @JsonView(Views.Short.class)
    private String subredditShortId;

    @Nonnull
    @Column(name = "name", nullable = false)
    @JsonView(Views.Short.class)
    private String name;

    @Nonnull
    @Column(name = "title", nullable = false)
    @JsonView(Views.Short.class)
    private String title;

    @Nonnull
    @Column(name = "url", nullable = false)
    @JsonView(Views.Short.class)
    private String url;

    @Nonnull
    @Column(name = "summary", nullable = false)
    @JsonView(Views.Short.class)
    private String summary;

    @Nonnull
    @Column(name = "description", nullable = false)
    @JsonView(Views.Detailed.class)
    private String description;

    @Nonnull
    @Column(name = "submission_type", nullable = false)
    @JsonView(Views.Detailed.class)
    private String submissionType;

    @Nonnull
    @Column(name = "created_at", nullable = false)
    @JsonView(Views.Detailed.class)
    private Date createdAt;

    @Nonnull
    @Column(name = "updated_at", nullable = false)
    @JsonView(Views.Detailed.class)
    private Date updatedAt;

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public String getSubredditShortId() {
        return subredditShortId;
    }

    public void setSubredditShortId(final String subredditShortId) {
        this.subredditShortId = subredditShortId;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(final String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getSubmissionType() {
        return submissionType;
    }

    public void setSubmissionType(final String submissionType) {
        this.submissionType = submissionType;
    }

    public Date getCreatedAt() {
        return new Date(createdAt.getTime());
    }

    public void setCreatedAt(final Date createdAt) {
        this.createdAt = new Date(createdAt.getTime());
    }

    public Date getUpdatedAt() {
        return new Date(updatedAt.getTime());
    }

    public void setUpdatedAt(final Date updatedAt) {
        this.updatedAt = new Date(updatedAt.getTime());
    }

}
