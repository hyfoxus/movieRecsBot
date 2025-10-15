package com.gnemirko.imdbvec.web;

import com.gnemirko.imdbvec.repo.MovieRepository;
import com.gnemirko.imdbvec.service.EmbeddingService;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SearchController {
    private final MovieRepository repo;
    private final EmbeddingService es;

    @GetMapping("/search/knn")
    public List<Map<String, Object>> search(@RequestParam String q, @RequestParam(defaultValue = "10") int k) throws Exception {
        float[] v = es.embed(q);
        PGvector vec = new PGvector(v);
        return repo.knn(vec, k).stream().map(row -> Map.of(
                "id", row[0],
                "tconst", row[1],
                "title", row[2],
                "plot", row[3],
                "rating", row[4],
                "cosine", row[5]
        )).toList();
    }
}
