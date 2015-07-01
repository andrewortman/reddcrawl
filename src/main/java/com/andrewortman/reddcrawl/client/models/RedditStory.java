package com.andrewortman.reddcrawl.client.models;

import com.andrewortman.reddcrawl.client.models.meta.RedditKind;
import com.andrewortman.reddcrawl.client.models.meta.RedditModel;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Date;

@RedditModel(kind = RedditKind.STORY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RedditStory extends RedditThing {

    @Nonnull
    private final String id;

    @Nonnull
    private final String title;

    @Nonnull
    private final String author;

    @Nonnull
    private final Date createdAt;

    @Nonnull
    private final String domain;

    @Nonnull
    private final Boolean isSelf;

    @Nonnull
    private final Integer numComments;

    @Nonnull
    private final Integer gilded;

    @Nonnull
    private final Integer score;

    @Nonnull
    private final Boolean over18;

    @Nonnull
    private final String permalink;

    @Nullable
    private final String selftext;

    @Nonnull
    private final String subreddit;

    @Nullable
    private final String thumbnail;

    @Nonnull
    private final String url;

    @Nullable
    private final String distinguished;

    @Nonnull
    private final Boolean stickied;

    @JsonCreator
    public RedditStory(@JsonProperty("id") @Nonnull final String id,
                       @JsonProperty("title") @Nonnull final String title,
                       @JsonProperty("author") @Nonnull final String author,
                       @JsonProperty("created_utc") @Nonnull final Long createdAt,
                       @JsonProperty("domain") @Nonnull final String domain,
                       @JsonProperty("is_self") @Nonnull final Boolean isSelf,
                       @JsonProperty("num_comments") @Nonnull final Integer numComments,
                       @JsonProperty("gilded") @Nonnull final Integer gilded,
                       @JsonProperty("score") @Nonnull final Integer score,
                       @JsonProperty("over_18") @Nonnull final Boolean over18,
                       @JsonProperty("permalink") @Nonnull final String permalink,
                       @JsonProperty("selftext") @Nullable final String selftext,
                       @JsonProperty("subreddit") @Nonnull final String subreddit,
                       @JsonProperty("thumbnail") @Nullable final String thumbnail,
                       @JsonProperty("url") @Nonnull final String url,
                       @JsonProperty("distinguished") @Nullable final String distinguished,
                       @JsonProperty("sticked") @Nonnull final Boolean stickied) {

        this.id = id;
        this.title = title;
        this.author = author;
        this.gilded = gilded;
        this.createdAt = new Date(createdAt * 1000L);
        this.domain = domain;
        this.isSelf = isSelf;
        this.numComments = numComments;
        this.score = score;
        this.over18 = over18;
        this.permalink = permalink;
        this.selftext = Strings.emptyToNull(selftext);
        this.subreddit = subreddit;
        this.thumbnail = Strings.emptyToNull(thumbnail);
        this.url = url;
        this.distinguished = distinguished;
        this.stickied = stickied;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public Date getCreatedAt() {
        return new Date(createdAt.getTime());
    }

    public String getDomain() {
        return domain;
    }

    public Boolean getIsSelf() {
        return isSelf;
    }

    public Integer getNumComments() {
        return numComments;
    }

    public Integer getGilded() {
        return gilded;
    }

    public Integer getScore() {
        return score;
    }

    public Boolean getOver18() {
        return over18;
    }

    public String getPermalink() {
        return permalink;
    }

    public String getSelftext() {
        return selftext;
    }

    public String getSubreddit() {
        return subreddit;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public String getUrl() {
        return url;
    }

    public String getDistinguished() {
        return distinguished;
    }

    public Boolean getStickied() {
        return stickied;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RedditStory that = (RedditStory) o;
        return Objects.equal(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String getFullId() {
        return RedditKind.STORY.getKey() + "_" + getId();
    }

    @JsonIgnore
    public Double getHotness() {
        //See http://www.outofscope.com/reddits-empire-no-longer-founded-on-a-flawed-algorithm/
        final double s = (double) this.score;
        final double order = Math.log10(Math.max(Math.abs(s), 1));

        final double sign;
        if (s > 0) sign = 1;
        else if (s < 0) sign = -1;
        else sign = 0;

        final double seconds = (getCreatedAt().getTime() / 1000.0) - 1134028003;

        return (sign * order) + (seconds / 45000.0);
    }
}
