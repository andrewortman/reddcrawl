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

    private final int numComments;

    private final int gilded;

    private final int score;

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

    private final boolean isSelf;

    private final boolean over18;

    private final boolean stickied;

    @JsonCreator
    public RedditStory(@JsonProperty("id") @Nonnull final String id,
                       @JsonProperty("title") @Nonnull final String title,
                       @JsonProperty("author") @Nonnull final String author,
                       @JsonProperty("created_utc") final long createdAt,
                       @JsonProperty("domain") @Nonnull final String domain,
                       @JsonProperty("num_comments") final int numComments,
                       @JsonProperty("gilded") final int gilded,
                       @JsonProperty("score") final int score,
                       @JsonProperty("permalink") @Nonnull final String permalink,
                       @JsonProperty("selftext") @Nullable final String selftext,
                       @JsonProperty("subreddit") @Nonnull final String subreddit,
                       @JsonProperty("thumbnail") @Nullable final String thumbnail,
                       @JsonProperty("url") @Nonnull final String url,
                       @JsonProperty("distinguished") @Nullable final String distinguished,
                       @JsonProperty("is_self") final boolean isSelf,
                       @JsonProperty("over_18") final boolean over18,
                       @JsonProperty("sticked") final boolean stickied) {

        this.id = id;
        this.title = title;
        this.author = author;
        this.gilded = gilded;
        this.createdAt = new Date(createdAt * 1000L);
        this.domain = domain;
        this.numComments = numComments;
        this.score = score;
        this.permalink = permalink;
        this.selftext = Strings.emptyToNull(selftext);
        this.subreddit = subreddit;
        this.thumbnail = Strings.emptyToNull(thumbnail);
        this.url = url;
        this.distinguished = distinguished;
        this.isSelf = isSelf;
        this.over18 = over18;
        this.stickied = stickied;
    }

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public String getTitle() {
        return title;
    }

    @Nonnull
    public String getAuthor() {
        return author;
    }

    @Nonnull
    public Date getCreatedAt() {
        return new Date(createdAt.getTime());
    }

    @Nonnull
    public String getDomain() {
        return domain;
    }

    public boolean getIsSelf() {
        return isSelf;
    }

    public int getNumComments() {
        return numComments;
    }

    public int getGilded() {
        return gilded;
    }

    public int getScore() {
        return score;
    }

    public boolean getOver18() {
        return over18;
    }

    @Nonnull
    public String getPermalink() {
        return permalink;
    }

    @Nullable
    public String getSelftext() {
        return selftext;
    }

    @Nonnull
    public String getSubreddit() {
        return subreddit;
    }

    @Nullable
    public String getThumbnail() {
        return thumbnail;
    }

    @Nonnull
    public String getUrl() {
        return url;
    }

    @Nullable
    public String getDistinguished() {
        return distinguished;
    }

    public boolean getStickied() {
        return stickied;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RedditStory that = (RedditStory) o;
        return Objects.equal(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Nonnull
    @Override
    public String getFullId() {
        return RedditKind.STORY.getKey() + "_" + getId();
    }

    @Nonnull
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
