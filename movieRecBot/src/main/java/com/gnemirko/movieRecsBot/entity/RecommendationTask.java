package com.gnemirko.movieRecsBot.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "rec_tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecommendationTask {

    public enum Status { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private Long userId;

    @Column(length = 4000)
    private String prompt;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    @Column(length = 4000)
    private String error;

    @Column(length = 8000)
    private String resultText;
}