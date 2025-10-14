package com.gnemirko.imdbvec.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VectorIndexService {
    @PersistenceContext private EntityManager em;

    @Transactional
    public void ensureHnswIndex() {
        em.createNativeQuery("CREATE EXTENSION IF NOT EXISTS vector").executeUpdate();
        em.createNativeQuery("""

          DO $$

          BEGIN

            IF NOT EXISTS (

              SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace

              WHERE c.relname='movie_embedding_hnsw' AND n.nspname=current_schema()

            ) THEN

              EXECUTE 'CREATE INDEX movie_embedding_hnsw ON movie USING hnsw (embedding vector_cosine_ops)';

            END IF;

          END$$;

        """).executeUpdate();
    }
}
