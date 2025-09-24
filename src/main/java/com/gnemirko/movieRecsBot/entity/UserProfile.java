package com.gnemirko.movieRecsBot.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserProfile {

    @Id
    private Long telegramUserId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_liked_genres", joinColumns = @JoinColumn(name = "tg_user_id"))
    @Column(name = "genre")
    private Set<String> likedGenres = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_liked_actors", joinColumns = @JoinColumn(name = "tg_user_id"))
    @Column(name = "actor")
    private Set<String> likedActors = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_liked_directors", joinColumns = @JoinColumn(name = "tg_user_id"))
    @Column(name = "director")
    private Set<String> likedDirectors = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_blocked_tags", joinColumns = @JoinColumn(name = "tg_user_id"))
    @Column(name = "tag")
    private Set<String> blocked = new LinkedHashSet<>();
}