package com.gnemirko.imdbvec.repo;

import com.gnemirko.imdbvec.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    Optional<Movie> findByTconst(String tconst);

    List<Movie> findTop500ByEmbeddingIsNullOrderByIdAsc();
}
