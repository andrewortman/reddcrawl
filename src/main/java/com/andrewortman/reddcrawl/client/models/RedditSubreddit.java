package com.andrewortman.reddcrawl.client.models;

import com.andrewortman.reddcrawl.client.models.meta.RedditKind;
import com.andrewortman.reddcrawl.client.models.meta.RedditModel;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nonnull;
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

    @Nonnull
    private final Integer commentScoreHideMins;

    @Nonnull
    private final Long subscribers;

    @Nonnull
    private final Integer active;

    @Nonnull
    private final Boolean over18;

    @Nonnull
    private final String url;


    @JsonCreator
    public RedditSubreddit(@JsonProperty("id") @Nonnull final String id,
                           @JsonProperty("display_name") final String displayName,
                           @JsonProperty("created") final Long createdAt,
                           @JsonProperty("title") final String title,
                           @JsonProperty("public_description") final String publicDescription,
                           @JsonProperty("description") @Nonnull final String description,
                           @JsonProperty("subreddit_type") @Nonnull final String subredditType,
                           @JsonProperty("submission_type") @Nonnull final String submissionType,
                           @JsonProperty("comment_score_hide_mins") @Nonnull final Integer commentScoreHideMins,
                           @JsonProperty("subscribers") @Nonnull final Long subscribers,
                           @JsonProperty("accounts_active") @Nonnull final Integer active,
                           @JsonProperty("over18") @Nonnull final Boolean over18,
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

    public String getId() {
        return id;
    }

    public String getName() {
        return displayName;
    }

    public Date getCreatedAt() {
        return new Date(createdAt.getTime());
    }

    public String getTitle() {
        return title;
    }

    public String getPublicDescription() {
        return publicDescription;
    }

    public String getDescription() {
        return description;
    }

    public String getSubredditType() {
        return subredditType;
    }

    public String getSubmissionType() {
        return submissionType;
    }

    public Integer getCommentScoreHideMins() {
        return commentScoreHideMins;
    }

    public Long getSubscribers() {
        return subscribers;
    }

    public Integer getActive() {
        return active;
    }

    public Boolean getOver18() {
        return over18;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String getFullId() {
        return RedditKind.SUBREDDIT.getKey() + "_" + getId();
    }

    @Override
    public boolean equals(final Object o) {
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
