package com.gnemirko.imdbvec.web;

import com.gnemirko.imdbvec.service.EmbeddingService;
import com.gnemirko.imdbvec.repo.MovieJdbc;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.Locale;

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
                                  @RequestParam(defaultValue = "10") int k,
                                  @RequestParam(name = "actors", required = false) List<String> actorNames) throws Exception {
        String joined = String.join(". ", q);
        float[] v = es.embed(joined);

        var rows = movieJdbc.topN(
                v,
                new String[] {},  // includeGenres (keep empty for now)
                new String[] {},  // excludeGenres
                toActorArray(actorNames),
                null, null,       // fromYear/toYear
                null, null,       // runtimeMax/minRating
                k
        );

        return rows.stream()
                .map(r -> new ResultDto(
                        r.tconst(),
                        r.title(),
                        r.year(),
                        r.rating(),
                        r.votes(),
                        r.similarity(),
                        safeGenres(r.genres()),
                        actorSummaries(r),
                        buildMetadata(r)
                ))
                .toList();
    }

    private String[] toActorArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new String[0];
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .toArray(String[]::new);
    }

    private List<String> safeGenres(String[] genres) {
        if (genres == null || genres.length == 0) {
            return List.of();
        }
        return Arrays.stream(genres)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<PersonDto> actorSummaries(MovieJdbc.RecoRow row) {
        List<String> names = row.actorNames();
        List<String> ids = row.actorIds();
        int size = Math.min(names.size(), ids.size());
        if (size == 0) {
            return List.of();
        }
        return IntStream.range(0, size)
                .mapToObj(i -> new PersonDto(ids.get(i), names.get(i)))
                .toList();
    }

    private Map<String, Object> buildMetadata(MovieJdbc.RecoRow row) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (row.plot() != null && !row.plot().isBlank()) {
            metadata.put("plot", row.plot().trim());
        }
        return metadata.isEmpty() ? null : metadata;
    }

    public record ResultDto(String tconst,
                            String title,
                            Short year,
                            Double rating,
                            Integer votes,
                            double similarity,
                            List<String> genres,
                            List<PersonDto> actors,
                            Map<String, Object> metadata) {}

    public record PersonDto(String id, String name) {}
}
