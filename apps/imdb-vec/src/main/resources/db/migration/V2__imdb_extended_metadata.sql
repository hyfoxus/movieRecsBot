ALTER TABLE movie
    ADD COLUMN IF NOT EXISTS akas jsonb,
    ADD COLUMN IF NOT EXISTS directors jsonb,
    ADD COLUMN IF NOT EXISTS writers jsonb,
    ADD COLUMN IF NOT EXISTS principals jsonb,
    ADD COLUMN IF NOT EXISTS episode jsonb;
