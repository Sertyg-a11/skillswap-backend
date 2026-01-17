-- MESSAGE SERVICE INITIAL SCHEMA (derived-query friendly)

CREATE TABLE conversations (
                               id              UUID PRIMARY KEY,
                               user_low_id     UUID NOT NULL,
                               user_high_id    UUID NOT NULL,
                               created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                               last_message_at TIMESTAMPTZ,

                               CONSTRAINT chk_distinct_users CHECK (user_low_id <> user_high_id),
                               CONSTRAINT ux_conversations_pair UNIQUE (user_low_id, user_high_id)
);

CREATE INDEX idx_conversations_low  ON conversations(user_low_id);
CREATE INDEX idx_conversations_high ON conversations(user_high_id);
CREATE INDEX idx_conversations_last_message ON conversations(last_message_at DESC);

CREATE TABLE messages (
                          id              UUID PRIMARY KEY,
                          conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                          sender_id       UUID NOT NULL,
                          recipient_id    UUID NOT NULL,
                          body            VARCHAR(2000) NOT NULL,
                          created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                          read_at         TIMESTAMPTZ
);

CREATE INDEX idx_messages_conversation_created
    ON messages(conversation_id, created_at DESC);

CREATE INDEX idx_messages_recipient_unread
    ON messages(recipient_id)
    WHERE read_at IS NULL;
