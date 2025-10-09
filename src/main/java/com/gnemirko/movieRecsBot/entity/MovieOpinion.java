package com.gnemirko.movieRecsBot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieOpinion {

    @Column(name = "movie_title", nullable = false, length = 300)
    private String title;

    @Column(name = "movie_opinion", length = 1000)
    private String opinion;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
