package com.gnemirko.imdbvec.service;

import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import com.gnemirko.imdbvec.model.Movie;
import com.gnemirko.imdbvec.repo.MovieRepository;
import com.pgvector.PGvector;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final MovieRepository repo;

    @Value("${app.embedding.modelUrl}")
    private String modelUrl;

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    @PostConstruct
    void init() throws IOException, ModelException {
        model = Criteria.builder()
            .setTypes(String.class, float[].class)
            .optEngine("OnnxRuntime")
            .optModelUrls(modelUrl)
            .optTranslatorFactory(new ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory())
            .build()
            .loadModel();
        predictor = model.newPredictor();
    }

    @PreDestroy
    void close() {
        if (predictor != null) predictor.close();
        if (model != null) model.close();
    }

    public float[] embed(String text) throws TranslateException { return predictor.predict(text == null ? "" : text); }

    @Transactional
    public void backfillEmbeddings() throws TranslateException {
        while (true) {
            List<Movie> batch = repo.findBatchWithoutEmbedding();
            if (batch.isEmpty()) break;
            for (Movie m : batch) {
                String text = (m.getTitle() == null ? "" : m.getTitle()) + " " + (m.getPlot() == null ? "" : m.getPlot());
                float[] vec = embed(text);
                m.setEmbedding(new PGvector(vec));
                repo.save(m);
            }
        }
    }
}
