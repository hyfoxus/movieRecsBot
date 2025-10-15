package com.gnemirko.imdbvec.repo;

import com.gnemirko.imdbvec.model.Movie;
import com.pgvector.PGvector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    @Query(value = "SELECT * FROM movie WHERE embedding IS NULL LIMIT 1000", nativeQuery = true)
    List<Movie> findBatchWithoutEmbedding();

    @Query(value = """
            
            SELECT id, tconst, title, plot, rating,
            
                   1 - (embedding <=> :q) AS cosine
            
            FROM movie
            
            WHERE embedding IS NOT NULL
            
            ORDER BY embedding <=> :q
            
            LIMIT :k
            
            """, nativeQuery = true)
    List<Object[]> knn(@Param("q") PGvector queryVec, @Param("k") int k);
}
