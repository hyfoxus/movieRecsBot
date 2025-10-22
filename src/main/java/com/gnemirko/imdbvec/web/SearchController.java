package com.gnemirko.imdbvec.web;

import com.gnemirko.imdbvec.service.EmbeddingService;
import com.gnemirko.imdbvec.repo.MovieJdbc;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchController {

    private final EmbeddingService es;
    private final MovieJdbc movieJdbc;

    public SearchController(EmbeddingService es, MovieJdbc movieJdbc) {
        this.es = es;
        this.movieJdbc = movieJdbc;
    }

    // keep your existing endpoint shape but fix varargs
    @GetMapping("/search/knn")
    public List<ResultDto> search(@RequestParam("q") String[] q,
                                  @RequestParam(defaultValue = "10") int k) throws Exception {
        String joined = String.join(". ", q);
        float[] v = es.embed(joined);

        var rows = movieJdbc.topN(
                v,
                new String[] {},  // includeGenres (keep empty for now)
                new String[] {},  // excludeGenres
                null, null,       // fromYear/toYear
                null, null,       // runtimeMax/minRating
                k
        );

        return rows.stream()
                .map(r -> new ResultDto(r.tconst(), r.title(), r.year(), r.rating(), r.votes(), r.similarity()))
                .toList();
    }

    public record ResultDto(String tconst, String title, Short year, Double rating, Integer votes, double similarity) {}
}