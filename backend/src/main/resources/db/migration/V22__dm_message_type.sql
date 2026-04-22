ALTER TABLE direct_messages
    ADD COLUMN message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN ref_id UUID;

CREATE INDEX idx_direct_messages_ref ON direct_messages(ref_id) WHERE ref_id IS NOT NULL;
