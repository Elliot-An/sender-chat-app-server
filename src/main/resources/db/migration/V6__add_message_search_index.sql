CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION immutable_unaccent(text)
RETURNS text
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT public.unaccent('public.unaccent', $1)
$$;

CREATE INDEX idx_messages_search_vector
    ON messages USING GIN (to_tsvector('simple', immutable_unaccent(body)));
