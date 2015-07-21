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
@Entity(name = "story_history")
public class StoryHistoryModel {
    @Id
    @JoinColumn(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Nonnull
    @ManyToOne(optional = false)
    @JoinColumn(name = "story", nullable = false)
    private StoryModel story;

    @Nonnull
    @Column(name = "timestamp", nullable = false)
    private Date timestamp;

    @Column(name = "score", nullable = true)
    private int score;

    @Column(name = "hotness", nullable = false)
    private double hotness;

    @Column(name = "comments", nullable = false)
    private int comments;

    @Column(name = "gilded", nullable = false)
    private int gilded;

    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    @Nonnull
    public StoryModel getStory() {
        return story;
    }

    public void setStory(@Nonnull final StoryModel story) {
        this.story = story;
    }

    @Nonnull
    public Date getTimestamp() {
        return new Date(timestamp.getTime());
    }

    public void setTimestamp(@Nonnull final Date timestamp) {
        this.timestamp = new Date(timestamp.getTime());
    }

    public int getScore() {
        return score;
    }

    public void setScore(final int score) {
        this.score = score;
    }

    public double getHotness() {
        return hotness;
    }

    public void setHotness(final double hotness) {
        this.hotness = hotness;
    }

    public int getComments() {
        return comments;
    }

    public void setComments(final int comments) {
        this.comments = comments;
    }

    public int getGilded() {
        return gilded;
    }

    public void setGilded(final int gilded) {
        this.gilded = gilded;
    }

}
