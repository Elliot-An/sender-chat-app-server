CREATE INDEX idx_messages_conversation_attachments
    ON messages (conversation_id, created_at DESC, id DESC)
    WHERE jsonb_array_length(attachments) > 0;
