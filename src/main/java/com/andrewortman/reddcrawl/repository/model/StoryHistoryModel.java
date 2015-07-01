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
@Entity(name = "story_history")
public class StoryHistoryModel {
    @Id
    @Nonnull
    @JoinColumn(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(Views.Detailed.class)
    private Long id;

    @Nonnull
    @ManyToOne(optional = false)
    @JoinColumn(name = "story", nullable = false)
    @JsonIgnore
    private StoryModel story;

    @Nonnull
    @Column(name = "timestamp", nullable = false)
    @JsonView(Views.Short.class)
    private Date timestamp;

    @Nonnull
    @Column(name = "score", nullable = true)
    @JsonView(Views.Short.class)
    private Integer score;

    @Nonnull
    @Column(name = "hotness", nullable = false)
    @JsonView(Views.Short.class)
    private Double hotness;

    @Nonnull
    @Column(name = "comments", nullable = false)
    @JsonView(Views.Short.class)
    private Integer comments;

    @Nonnull
    @Column(name = "gilded", nullable = false)
    @JsonView(Views.Short.class)
    private Integer gilded;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    @JsonIgnore
    public StoryModel getStory() {
        return story;
    }

    public void setStory(final StoryModel story) {
        this.story = story;
    }

    public Date getTimestamp() {
        return new Date(timestamp.getTime());
    }

    public void setTimestamp(final Date timestamp) {
        this.timestamp = new Date(timestamp.getTime());
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(final Integer score) {
        this.score = score;
    }

    public Double getHotness() {
        return hotness;
    }

    public void setHotness(final Double hotness) {
        this.hotness = hotness;
    }

    public Integer getComments() {
        return comments;
    }

    public void setComments(final Integer comments) {
        this.comments = comments;
    }

    public Integer getGilded() {
        return gilded;
    }

    public void setGilded(final Integer gilded) {
        this.gilded = gilded;
    }
}
