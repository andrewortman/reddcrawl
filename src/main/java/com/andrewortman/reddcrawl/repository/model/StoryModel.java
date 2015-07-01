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
import java.util.Collection;
import java.util.Date;

/**
 * Holds the representation of a reddit story in the database
 */
@Entity(name = "story")
public class StoryModel {
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

    @Nonnull
    @Column(name = "updated_at", nullable = false)
    @JsonView(Views.Short.class)
    private Date updatedAt;

    @OneToMany(targetEntity = StoryHistoryModel.class, mappedBy = "story")
    private Collection<StoryHistoryModel> history;

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public String getRedditShortId() {
        return redditShortId;
    }

    public void setRedditShortId(final String id) {
        this.redditShortId = id;
    }

    public SubredditModel getSubreddit() {
        return subreddit;
    }

    public void setSubreddit(final SubredditModel subreddit) {
        this.subreddit = subreddit;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(final String author) {
        this.author = author;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getPermalink() {
        return permalink;
    }

    public void setPermalink(final String permalink) {
        this.permalink = permalink;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(final String domain) {
        this.domain = domain;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(final String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getDistinguished() {
        return distinguished;
    }

    public void setDistinguished(final String distinguished) {
        this.distinguished = distinguished;
    }

    public Boolean getOver18() {
        return over18;
    }

    public void setOver18(final Boolean over18) {
        this.over18 = over18;
    }

    public Boolean getStickied() {
        return stickied;
    }

    public void setStickied(final Boolean stickied) {
        this.stickied = stickied;
    }

    public Boolean getIsSelf() {
        return isSelf;
    }

    public void setIsSelf(final Boolean isSelf) {
        this.isSelf = isSelf;
    }

    public String getSelftext() {
        return selftext;
    }

    public void setSelftext(final String selftext) {
        this.selftext = selftext;
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

    public Date getCreatedAt() {
        return new Date(createdAt.getTime());
    }

    public void setCreatedAt(final Date createdAt) {
        this.createdAt = new Date(createdAt.getTime());
    }

    public Date getDiscoveredAt() {
        return new Date(discoveredAt.getTime());
    }

    public void setDiscoveredAt(final Date discoveredAt) {
        this.discoveredAt = new Date(discoveredAt.getTime());
    }

    public Date getUpdatedAt() {
        return new Date(updatedAt.getTime());
    }

    public void setUpdatedAt(final Date updatedAt) {
        this.updatedAt = new Date(updatedAt.getTime());
    }

    @JsonIgnore
    public Collection<StoryHistoryModel> getHistory() {
        return this.history;
    }

    ;

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
        final Collection<StoryHistoryModel> historyModels = getHistory();

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
