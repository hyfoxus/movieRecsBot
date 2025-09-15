package com.gnemirko.movieRecsBot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_message")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;

    @Column(length = 2000)
    private String text;

    private Instant createdAt;
}