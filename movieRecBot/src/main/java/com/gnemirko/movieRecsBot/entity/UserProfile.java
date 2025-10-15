package com.gnemirko.movieRecsBot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_movie_feedback", joinColumns = @JoinColumn(name = "tg_user_id"))
    @OrderBy("recordedAt DESC")
    private List<MovieOpinion> watchedMovies = new ArrayList<>();
}
