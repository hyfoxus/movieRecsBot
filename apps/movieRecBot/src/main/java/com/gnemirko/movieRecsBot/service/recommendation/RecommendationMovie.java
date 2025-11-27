package com.gnemirko.movieRecsBot.service.recommendation;

import java.util.HashSet;
import java.util.Set;

public class RecommendationMovie {
    private String title;
    private Integer year;
    private String reason;
    private Set<String> genres = new HashSet<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Set<String> getGenres() {
        return genres;
    }

    public void setGenres(Set<String> genres) {
        this.genres = genres;
    }
}
