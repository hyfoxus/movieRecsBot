ALTER TABLE movie
    DROP COLUMN IF EXISTS directors,
    DROP COLUMN IF EXISTS writers,
    DROP COLUMN IF EXISTS principals;
