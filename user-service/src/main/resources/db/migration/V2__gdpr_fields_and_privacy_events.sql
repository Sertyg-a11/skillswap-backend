-- V2__gdpr_fields_and_privacy_events.sql

-- 1) Extend users table with GDPR-related fields

ALTER TABLE users
    ADD COLUMN is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN allow_matching BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN allow_emails   BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN deleted_at     TIMESTAMPTZ  NULL;

-- Optional but recommended: index to quickly filter out deleted users
CREATE INDEX idx_users_not_deleted
    ON users (deleted_at);

-- 2) Privacy audit log for GDPR actions

CREATE TABLE privacy_events (
                                id          BIGSERIAL PRIMARY KEY,
                                user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                type        VARCHAR(64) NOT NULL,   -- stores enum name from PrivacyEventType
                                details     TEXT,
                                created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_privacy_events_user_created
    ON privacy_events (user_id, created_at DESC);
