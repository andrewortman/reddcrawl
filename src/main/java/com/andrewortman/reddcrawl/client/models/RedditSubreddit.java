package com.andrewortman.reddcrawl.client.models;

import com.andrewortman.reddcrawl.client.models.meta.RedditKind;
import com.andrewortman.reddcrawl.client.models.meta.RedditModel;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Date;

@RedditModel(kind = RedditKind.SUBREDDIT)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RedditSubreddit extends RedditThing {


    @Nonnull
    private final String id;

    @Nonnull
    private final String displayName;

    @Nonnull
    private final String title;

    @Nonnull
    private final Date createdAt;

    @Nonnull
    private final String publicDescription;

    @Nonnull
    private final String description;

    @Nonnull
    private final String subredditType;

    @Nonnull
    private final String submissionType;

    private final int commentScoreHideMins;

    private final long subscribers;

    private final int active;

    private final boolean over18;

    @Nonnull
    private final String url;


    @JsonCreator
    public RedditSubreddit(@JsonProperty("id") @Nonnull final String id,
                           @JsonProperty("display_name") @Nonnull final String displayName,
                           @JsonProperty("created") final long createdAt,
                           @JsonProperty("title") @Nonnull final String title,
                           @JsonProperty("public_description") @Nonnull final String publicDescription,
                           @JsonProperty("description") @Nonnull final String description,
                           @JsonProperty("subreddit_type") @Nonnull final String subredditType,
                           @JsonProperty("submission_type") @Nonnull final String submissionType,
                           @JsonProperty("comment_score_hide_mins") final int commentScoreHideMins,
                           @JsonProperty("subscribers") final long subscribers,
                           @JsonProperty("accounts_active") final int active,
                           @JsonProperty("over18") final boolean over18,
                           @JsonProperty("url") @Nonnull final String url) {

        this.id = id;
        this.displayName = displayName;
        this.createdAt = new Date(createdAt * 1000L);
        this.title = title;
        this.publicDescription = publicDescription;
        this.description = description;
        this.subredditType = subredditType;
        this.submissionType = submissionType;
        this.commentScoreHideMins = commentScoreHideMins;
        this.subscribers = subscribers;
        this.active = active;
        this.over18 = over18;
        this.url = url;
    }

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public String getName() {
        return displayName;
    }

    @Nonnull
    public Date getCreatedAt() {
        return new Date(createdAt.getTime());
    }

    @Nonnull
    public String getTitle() {
        return title;
    }

    @Nonnull
    public String getPublicDescription() {
        return publicDescription;
    }

    @Nonnull
    public String getDescription() {
        return description;
    }

    @Nonnull
    public String getSubredditType() {
        return subredditType;
    }

    @Nonnull
    public String getSubmissionType() {
        return submissionType;
    }

    public int getCommentScoreHideMins() {
        return commentScoreHideMins;
    }

    public long getSubscribers() {
        return subscribers;
    }

    public int getActive() {
        return active;
    }

    public boolean getOver18() {
        return over18;
    }

    @Nonnull
    public String getUrl() {
        return url;
    }

    @Nonnull
    @Override
    public String getFullId() {
        return RedditKind.SUBREDDIT.getKey() + "_" + getId();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RedditSubreddit subreddit = (RedditSubreddit) o;
        return Objects.equal(getId(), subreddit.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
