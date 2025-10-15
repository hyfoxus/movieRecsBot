package com.gnemirko.imdbvec.model;

import com.pgvector.PGvector;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "movie")
@Getter
@Setter
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tconst;

    @Column(nullable = false)
    private String title;

    private Integer year;

    @Column(columnDefinition = "text[]")
    private List<String> genres;

    @Column(columnDefinition = "text")
    private String plot;

    private Double rating;
    private Integer votes;

    @Column(columnDefinition = "vector(384)")
    @Convert(converter = PGVectorConverter.class)
    private PGvector embedding;

}
