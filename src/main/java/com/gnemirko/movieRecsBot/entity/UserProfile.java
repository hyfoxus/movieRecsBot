package com.gnemirko.movieRecsBot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "app_user", indexes = {
        @Index(name = "ux_app_user_chat_id", columnList = "chat_id", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false, unique = true)
    private Long chatId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_pref_genre", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "genre", length = 100)
    @Builder.Default
    private Set<String> preferredGenres = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_pref_actor", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "actor", length = 150)
    @Builder.Default
    private Set<String> preferredActors = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_pref_director", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "director", length = 150)
    @Builder.Default
    private Set<String> preferredDirectors = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_anti", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "tag", length = 150)
    @Builder.Default
    private Set<String> antiPreferences = new LinkedHashSet<>();

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}