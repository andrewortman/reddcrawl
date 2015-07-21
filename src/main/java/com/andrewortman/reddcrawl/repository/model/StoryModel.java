package com.andrewortman.reddcrawl.repository.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Date;

/**
 * Holds the representation of a reddit story in the database
 */
@SuppressWarnings("NullableProblems")
@Entity(name = "story")
public class StoryModel {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Nonnull
    @Column(name = "reddit_short_id", nullable = false)
    private String redditShortId;

    @Nonnull
    @ManyToOne(optional = false)
    @JoinColumn(name = "subreddit", nullable = false)
    private SubredditModel subreddit;

    @Nonnull
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "author", nullable = true)
    private String author;

    @Nonnull
    @Column(name = "url", nullable = false)
    private String url;

    @Nonnull
    @Column(name = "permalink", nullable = false)
    private String permalink;

    @Nonnull
    @Column(name = "domain", nullable = false)
    private String domain;

    @Nullable
    @Column(name = "thumbnail", nullable = true)
    private String thumbnail;

    @Column(name = "distinguished", nullable = true)
    private String distinguished;

    @Column(name = "over18", nullable = false)
    private boolean over18;

    @Column(name = "stickied", nullable = false)
    private boolean stickied;

    @Column(name = "is_self", nullable = false)
    private boolean isSelf;

    @Nullable
    @Column(name = "selftext", nullable = true)
    private String selftext;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "hotness", nullable = false)
    private double hotness;

    @Column(name = "comments", nullable = false)
    private int comments;

    @Column(name = "gilded", nullable = false)
    private int gilded;

    @Nonnull
    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Nonnull
    @Column(name = "discovered_at", nullable = false)
    private Date discoveredAt;

    //this is the last time the score/hotness/gilded was updated.. meaning a valid history item occurred.
    @Nonnull
    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;

    //this is to mark the last time we checked the story history. this does not mean the story item was updated
    //for example, if the story was deleted or hidden (like the subreddit went private) - this would be set but updatedAt
    //would be behind. I put this in here after the reddit blackout of July 2015 caused a lot of subreddits to go private
    @Nonnull
    @Column(name = "checked_at", nullable = false)
    private Date checkedAt;

    public int getId() {
        return id;
    }

    public void setId(final int id) {
        this.id = id;
    }

    @Nonnull
    public String getRedditShortId() {
        return redditShortId;
    }

    public void setRedditShortId(@Nonnull final String id) {
        this.redditShortId = id;
    }

    @Nonnull
    public SubredditModel getSubreddit() {
        return subreddit;
    }

    public void setSubreddit(@Nonnull final SubredditModel subreddit) {
        this.subreddit = subreddit;
    }

    @Nonnull
    public String getTitle() {
        return title;
    }

    public void setTitle(@Nonnull final String title) {
        this.title = title;
    }

    @Nonnull
    public String getAuthor() {
        return author;
    }

    public void setAuthor(@Nonnull final String author) {
        this.author = author;
    }

    @Nonnull
    public String getUrl() {
        return url;
    }

    public void setUrl(@Nonnull final String url) {
        this.url = url;
    }

    @Nonnull
    public String getPermalink() {
        return permalink;
    }

    public void setPermalink(@Nonnull final String permalink) {
        this.permalink = permalink;
    }

    @Nonnull
    public String getDomain() {
        return domain;
    }

    public void setDomain(@Nonnull final String domain) {
        this.domain = domain;
    }

    @Nullable
    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(@Nullable final String thumbnail) {
        this.thumbnail = thumbnail;
    }

    @Nullable
    public String getDistinguished() {
        return distinguished;
    }

    public void setDistinguished(@Nullable final String distinguished) {
        this.distinguished = distinguished;
    }

    public boolean getOver18() {
        return over18;
    }

    public void setOver18(final boolean over18) {
        this.over18 = over18;
    }

    public boolean getStickied() {
        return stickied;
    }

    public void setStickied(final boolean stickied) {
        this.stickied = stickied;
    }

    public boolean getIsSelf() {
        return isSelf;
    }

    public void setIsSelf(final boolean isSelf) {
        this.isSelf = isSelf;
    }

    @Nullable
    public String getSelftext() {
        return selftext;
    }

    public void setSelftext(@Nullable final String selftext) {
        this.selftext = selftext;
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

    @Nonnull
    public Date getCreatedAt() {
        return new Date(createdAt.getTime());
    }

    public void setCreatedAt(@Nonnull final Date createdAt) {
        this.createdAt = new Date(createdAt.getTime());
    }

    @Nonnull
    public Date getDiscoveredAt() {
        return new Date(discoveredAt.getTime());
    }

    public void setDiscoveredAt(@Nonnull final Date discoveredAt) {
        this.discoveredAt = new Date(discoveredAt.getTime());
    }

    @Nonnull
    public Date getUpdatedAt() {
        return new Date(updatedAt.getTime());
    }

    public void setUpdatedAt(@Nonnull final Date updatedAt) {
        this.updatedAt = new Date(updatedAt.getTime());
    }

    @Nonnull
    public Date getCheckedAt() {
        return new Date(checkedAt.getTime());
    }

    public void setCheckedAt(@Nonnull final Date checkedAt) {
        this.checkedAt = new Date(checkedAt.getTime());
    }
}
