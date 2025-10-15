package com.gnemirko.imdbvec;

import com.gnemirko.imdbvec.service.EmbeddingService;
import com.gnemirko.imdbvec.service.ImdbImportService;
import com.gnemirko.imdbvec.service.VectorIndexService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ImdbVecApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImdbVecApplication.class, args);
    }

    @Bean
    CommandLineRunner init(ImdbImportService importer, EmbeddingService embeddings, VectorIndexService indexer) {
        return args -> {
            importer.importImdb();
            embeddings.backfillEmbeddings();
            indexer.ensureHnswIndex();
        };
    }
}
