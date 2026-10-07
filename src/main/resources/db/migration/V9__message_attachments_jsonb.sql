ALTER TABLE messages DROP CONSTRAINT ck_messages_body_not_blank;

ALTER TABLE messages ALTER COLUMN body DROP NOT NULL;

ALTER TABLE messages
    ADD COLUMN attachments JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE messages
    ADD CONSTRAINT ck_messages_body_optional
        CHECK (body IS NULL OR length(btrim(body)) BETWEEN 1 AND 4000);

ALTER TABLE messages
    ADD CONSTRAINT ck_messages_attachments_array
        CHECK (jsonb_typeof(attachments) = 'array' AND jsonb_array_length(attachments) BETWEEN 0 AND 5);

ALTER TABLE messages
    ADD CONSTRAINT ck_messages_has_body_or_attachment
        CHECK (body IS NOT NULL OR jsonb_array_length(attachments) >= 1);
