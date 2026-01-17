package com.gnemirko.imdbvec.repo;

import com.gnemirko.imdbvec.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    Optional<Movie> findByTconst(String tconst);

    List<Movie> findTop500ByEmbeddingModelIsNullOrderByIdAsc();

    @Query(value = """
            SELECT *
            FROM movie
            WHERE plot IS NULL
              AND id > :afterId
            ORDER BY id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Movie> findBatchMissingPlot(@Param("afterId") long afterId, @Param("limit") int limit);
}
