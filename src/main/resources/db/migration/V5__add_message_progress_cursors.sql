ALTER TABLE conversation_members
    ADD COLUMN last_delivered_message_id BIGINT,
    ADD COLUMN last_delivered_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_read_message_id BIGINT,
    ADD COLUMN last_read_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE conversation_members
    ADD CONSTRAINT fk_conversation_members_last_delivered_message
        FOREIGN KEY (last_delivered_message_id) REFERENCES messages (id),
    ADD CONSTRAINT fk_conversation_members_last_read_message
        FOREIGN KEY (last_read_message_id) REFERENCES messages (id);

CREATE INDEX idx_conversation_members_unread_cursor
    ON conversation_members (user_id, conversation_id, last_read_message_id);
