DROP TABLE IF EXISTS subreddit, subreddit_history, story, story_history CASCADE;

CREATE TABLE subreddit
(
  id              SERIAL                      NOT NULL,
  reddit_short_id TEXT                        NOT NULL,
  name            TEXT                        NOT NULL,
  title           TEXT                        NOT NULL,
  url             TEXT                        NOT NULL,
  summary         TEXT,
  description     TEXT                        NOT NULL,
  submission_type TEXT                        NOT NULL,
  created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  updated_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  CONSTRAINT subreddit_pkey PRIMARY KEY (id),
  CONSTRAINT unique_subrreddit_short_id UNIQUE (reddit_short_id),
  CONSTRAINT unique_subrreddit_name UNIQUE (name)
);


CREATE INDEX subreddit_names_idx
ON subreddit
USING BTREE
(name);

CREATE TABLE subreddit_history
(
  id                BIGSERIAL                   NOT NULL,
  "timestamp"       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  subreddit         INTEGER                     NOT NULL,
  subscribers       BIGINT                      NOT NULL,
  active            INTEGER                     NOT NULL,
  comment_hide_mins INTEGER                     NOT NULL,
  CONSTRAINT subreddit_history_pkey PRIMARY KEY (id),
  CONSTRAINT subreddit_fk FOREIGN KEY (subreddit)
  REFERENCES subreddit (id) MATCH SIMPLE
  ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE INDEX subreddit_history_subreddit_idx
ON subreddit_history
USING BTREE
("subreddit", "timestamp" DESC);

CREATE TABLE story
(
  id              SERIAL                      NOT NULL,
  reddit_short_id TEXT                        NOT NULL,
  subreddit       INTEGER                     NOT NULL,
  title           TEXT                        NOT NULL,
  author          TEXT,
  url             TEXT                        NOT NULL,
  permalink       TEXT                        NOT NULL,
  domain          TEXT                        NOT NULL,
  thumbnail       TEXT,
  distinguished   TEXT,
  over18          BOOLEAN                     NOT NULL,
  is_self         BOOLEAN                     NOT NULL,
  selftext        TEXT,
  stickied        BOOLEAN                     NOT NULL,
  created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  score           INTEGER                     NOT NULL,
  hotness         DOUBLE PRECISION            NOT NULL,
  comments        INTEGER                     NOT NULL,
  gilded          INTEGER                     NOT NULL,
  discovered_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  updated_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  checked_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  CONSTRAINT story_pkey PRIMARY KEY (id),
  CONSTRAINT unique_story_short_id UNIQUE (reddit_short_id),
  CONSTRAINT subreddit_fk FOREIGN KEY (subreddit)
  REFERENCES subreddit (id) MATCH SIMPLE
  ON UPDATE NO ACTION ON DELETE CASCADE
)
WITH (
OIDS =FALSE
);

CREATE INDEX story_hotness_idx
ON story
USING BTREE
(hotness DESC, checked_at DESC, created_at DESC);

CREATE INDEX story_subreddit_idx
ON story
USING BTREE
(subreddit);

CREATE TABLE story_history
(
  id          BIGSERIAL                   NOT NULL,
  "timestamp" TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  story       INTEGER                     NOT NULL,
  score       INTEGER                     NOT NULL,
  hotness     DOUBLE PRECISION            NOT NULL,
  comments    INTEGER                     NOT NULL,
  gilded      INTEGER                     NOT NULL,
  CONSTRAINT story_history_pkey PRIMARY KEY (id),
  CONSTRAINT story_fk FOREIGN KEY (story)
  REFERENCES story (id) MATCH SIMPLE
  ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE INDEX story_history_story_idx
ON story_history
USING BTREE
(story DESC);