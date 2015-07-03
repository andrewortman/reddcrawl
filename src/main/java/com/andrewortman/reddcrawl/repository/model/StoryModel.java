package com.andrewortman.reddcrawl.repository.model;

import com.andrewortman.reddcrawl.repository.Views;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.*;
import java.util.*;

/**
 * Holds the representation of a reddit story in the database
 */
@Entity(name = "story")
public class StoryModel {
    @Nonnull
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Nonnull
    @Column(name = "reddit_short_id", nullable = false)
    @JsonView(Views.Short.class)
    private String redditShortId;

    @Nonnull
    @ManyToOne(optional = false)
    @JoinColumn(name = "subreddit", nullable = false)
    @JsonView(Views.Short.class)
    private SubredditModel subreddit;

    @Nonnull
    @Column(name = "title", nullable = false)
    @JsonView(Views.Short.class)
    private String title;

    @Column(name = "author", nullable = true)
    @JsonView(Views.Short.class)
    private String author;

    @Nonnull
    @Column(name = "url", nullable = false)
    @JsonView(Views.Short.class)
    private String url;

    @Nonnull
    @Column(name = "permalink", nullable = false)
    @JsonView(Views.Short.class)
    private String permalink;

    @Nonnull
    @Column(name = "domain", nullable = false)
    @JsonView(Views.Short.class)
    private String domain;

    @Nullable
    @Column(name = "thumbnail", nullable = true)
    @JsonView(Views.Short.class)
    private String thumbnail;

    @Nullable
    @Column(name = "distinguished", nullable = true)
    @JsonView(Views.Short.class)
    private String distinguished;

    @Nonnull
    @Column(name = "over18", nullable = false)
    @JsonView(Views.Short.class)
    private Boolean over18;

    @Nonnull
    @Column(name = "stickied", nullable = false)
    @JsonView(Views.Short.class)
    private Boolean stickied;

    @Nonnull
    @Column(name = "is_self", nullable = false)
    @JsonView(Views.Short.class)
    private Boolean isSelf;

    @Nullable
    @Column(name = "selftext", nullable = true)
    @JsonView(Views.Detailed.class)
    private String selftext;

    @Nonnull
    @Column(name = "score", nullable = false)
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

    @Nonnull
    @Column(name = "created_at", nullable = false)
    @JsonView(Views.Short.class)
    private Date createdAt;

    @Nonnull
    @Column(name = "discovered_at", nullable = false)
    @JsonView(Views.Short.class)
    private Date discoveredAt;

    //this is the last time the score/hotness/gilded was updated.. meaning a valid history item occurred.
    @Nonnull
    @Column(name = "updated_at", nullable = false)
    @JsonView(Views.Short.class)
    private Date updatedAt;

    //this is to mark the last time we checked the story history. this does not mean the story item was updated
    //for example, if the story was deleted or hidden (like the subreddit went private) - this would be set but updatedAt
    //would be behind. I put this in here after the reddit blackout of July 2015 caused a lot of subreddits to go private
    @Nonnull
    @Column(name = "checked_at", nullable = false)
    @JsonView(Views.Short.class)
    private Date checkedAt;

    @OneToMany(targetEntity = StoryHistoryModel.class, mappedBy = "story")
    private Collection<StoryHistoryModel> history;

    @Nonnull
    public Integer getId() {
        return id;
    }

    public void setId(@Nonnull final Integer id) {
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

    public void setPermalink(final String permalink) {
        this.permalink = permalink;
    }

    @Nonnull
    public String getDomain() {
        return domain;
    }

    public void setDomain(@Nonnull final String domain) {
        this.domain = domain;
    }

    @Nonnull
    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(@Nonnull final String thumbnail) {
        this.thumbnail = thumbnail;
    }

    @Nonnull
    public String getDistinguished() {
        return distinguished;
    }

    public void setDistinguished(@Nonnull final String distinguished) {
        this.distinguished = distinguished;
    }

    @Nonnull
    public Boolean getOver18() {
        return over18;
    }

    public void setOver18(@Nonnull final Boolean over18) {
        this.over18 = over18;
    }

    @Nonnull
    public Boolean getStickied() {
        return stickied;
    }

    public void setStickied(@Nonnull final Boolean stickied) {
        this.stickied = stickied;
    }

    @Nonnull
    public Boolean getIsSelf() {
        return isSelf;
    }

    public void setIsSelf(@Nonnull final Boolean isSelf) {
        this.isSelf = isSelf;
    }

    @Nonnull
    public String getSelftext() {
        return selftext;
    }

    public void setSelftext(@Nonnull final String selftext) {
        this.selftext = selftext;
    }

    @Nonnull
    public Integer getScore() {
        return score;
    }

    public void setScore(@Nonnull final Integer score) {
        this.score = score;
    }

    @Nonnull
    public Double getHotness() {
        return hotness;
    }

    public void setHotness(@Nonnull final Double hotness) {
        this.hotness = hotness;
    }

    @Nonnull
    public Integer getComments() {
        return comments;
    }

    public void setComments(@Nonnull final Integer comments) {
        this.comments = comments;
    }

    @Nonnull
    public Integer getGilded() {
        return gilded;
    }

    public void setGilded(@Nonnull final Integer gilded) {
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

    public void setHistory(@Nonnull final Collection<StoryHistoryModel> history) {
        this.history = history;
    }

    @JsonIgnore
    public List<StoryHistoryModel> getHistory() {
        return new ArrayList<>(this.history);
    }

    @Nonnull
    @JsonProperty("history")
    @JsonView(Views.Detailed.class)
    public JsonNode getHistoryJSON() {
        final JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;
        final ObjectNode rootNode = jsonNodeFactory.objectNode();
        final ArrayNode columnHeaders = jsonNodeFactory.arrayNode();
        columnHeaders.add("timestamp");
        columnHeaders.add("score");
        columnHeaders.add("hotness");
        columnHeaders.add("comments");
        columnHeaders.add("gilded");
        rootNode.set("columns", columnHeaders);

        final ArrayNode historyItems = jsonNodeFactory.arrayNode();
        final List<StoryHistoryModel> historyModels = getHistory();

        //sort by timestamp
        Collections.sort(historyModels, new Comparator<StoryHistoryModel>() {
            @Override
            public int compare(final StoryHistoryModel o1, final StoryHistoryModel o2) {
                return o1.getTimestamp().compareTo(o2.getTimestamp());
            }
        });

        for (final StoryHistoryModel historyModel : historyModels) {
            final ArrayNode row = jsonNodeFactory.arrayNode();
            row.add(historyModel.getTimestamp().getTime());
            row.add(historyModel.getScore());
            row.add(historyModel.getHotness());
            row.add(historyModel.getComments());
            row.add(historyModel.getGilded());
            historyItems.add(row);
        }
        rootNode.set("data", historyItems);

        return rootNode;
    }
}
