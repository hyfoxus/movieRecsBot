package com.gnemirko.imdbvec.model;

import com.pgvector.PGvector;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "movie")
public class Movie {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTconst() { return tconst; }
    public void setTconst(String tconst) { this.tconst = tconst; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }
    public String getPlot() { return plot; }
    public void setPlot(String plot) { this.plot = plot; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public Integer getVotes() { return votes; }
    public void setVotes(Integer votes) { this.votes = votes; }
    public PGvector getEmbedding() { return embedding; }
    public void setEmbedding(PGvector embedding) { this.embedding = embedding; }
}
