CREATE TABLE friend_requests (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_friend_request UNIQUE (from_user_id, to_user_id)
);

CREATE INDEX idx_friend_requests_to_user   ON friend_requests(to_user_id, status);
CREATE INDEX idx_friend_requests_from_user ON friend_requests(from_user_id, status);

CREATE TABLE direct_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id VARCHAR(80) NOT NULL,
    sender_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text            TEXT NOT NULL,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at         TIMESTAMPTZ
);

CREATE INDEX idx_dm_conversation ON direct_messages(conversation_id, sent_at DESC);
CREATE INDEX idx_dm_user         ON direct_messages(sender_id, receiver_id);
