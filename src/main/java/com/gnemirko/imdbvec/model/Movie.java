package com.gnemirko.imdbvec.model;

import jakarta.persistence.Convert;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Getter @Setter
@Entity
@Table(name = "movie")
public class Movie {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tconst;

    private String primaryTitle;
    private String originalTitle;
    private String titleType;

    private Boolean isAdult;
    private Short startYear;
    private Short endYear;
    private Short runtimeMinutes;

    @Column(columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] genres;

    private Double rating;
    private Integer votes;

    @Column(columnDefinition = "text")
    private String plot;

    @Convert(converter = PGVectorConverter.class)
    private float[] embedding;

    private String embeddingModel;
    private OffsetDateTime embeddingUpdatedAt;
}
