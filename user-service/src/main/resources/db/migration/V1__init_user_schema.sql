-- USER-SERVICE INITIAL SCHEMA

CREATE TABLE users (
                       id           UUID PRIMARY KEY,
                       external_id  VARCHAR(64) UNIQUE NOT NULL,    -- later: Keycloak sub
                       email        VARCHAR(255) UNIQUE NOT NULL,
                       display_name VARCHAR(100) NOT NULL,
                       time_zone    VARCHAR(64),
                       bio          TEXT,
                       created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE skills (
                        id          UUID PRIMARY KEY,
                        user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        name        VARCHAR(100) NOT NULL,
                        level       VARCHAR(32),
                        category    VARCHAR(64),
                        description TEXT,
                        created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_skills_user   ON skills(user_id);
CREATE INDEX idx_skills_name   ON skills(lower(name));

CREATE TABLE messages (
                          id          BIGSERIAL PRIMARY KEY,
                          user_id     UUID NOT NULL,
                          content     VARCHAR(2000) NOT NULL,
                          created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_user_created
    ON messages(user_id, created_at DESC);
