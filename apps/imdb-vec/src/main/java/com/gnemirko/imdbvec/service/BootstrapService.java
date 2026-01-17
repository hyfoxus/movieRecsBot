package com.gnemirko.imdbvec.service;

import com.gnemirko.imdbvec.importer.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class BootstrapService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    private final ImportService importer;
    private final EmbeddingService embeddings;
    private final VectorIndexService indexer;
    private final MovieOverviewService overviewService;
    private final boolean skipEmbeddingsOnError;

    public BootstrapService(ImportService importer,
                            MovieOverviewService overviewService,
                            EmbeddingService embeddings,
                            VectorIndexService indexer,
                            @Value("${app.bootstrap.skipEmbeddingsOnError:true}") boolean skipEmbeddingsOnError) {
        this.importer = importer;
        this.overviewService = overviewService;
        this.embeddings = embeddings;
        this.indexer = indexer;
        this.skipEmbeddingsOnError = skipEmbeddingsOnError;
    }


    @Async
    public CompletableFuture<Void> runFullBootstrap(boolean rebuildIndex) {
        var future = new CompletableFuture<Void>();
        try {
            log.info("Bootstrap job started (rebuildIndex={})", rebuildIndex);
            importer.runFullImport();
            try {
                overviewService.backfillOverviews();
            } catch (Exception ex) {
                log.warn("TMDB overview backfill failed: {}", ex.getMessage());
            }
            try {
                embeddings.backfillEmbeddings();
            } catch (Exception ex) {
                if (skipEmbeddingsOnError) {
                    log.error("Embedding backfill failed; continuing without embeddings (set app.bootstrap.skipEmbeddingsOnError=false to fail).", ex);
                } else {
                    throw ex;
                }
            }
            if (rebuildIndex) {
                indexer.ensureHnswIndex();
            }
            log.info("Bootstrap job finished successfully");
            future.complete(null);
        } catch (Exception ex) {
            log.error("Bootstrap job failed", ex);
            future.completeExceptionally(ex);
        }
        return future;
    }

    @Async
    public CompletableFuture<Void> runTmdbOverviewBackfill(Long maxUpdates, Integer batchSize) {
        var future = new CompletableFuture<Void>();
        try {
            log.info("TMDB overview backfill job started (maxUpdates={}, batchSize={})", maxUpdates, batchSize);
            overviewService.backfillOverviews(maxUpdates, batchSize);
            log.info("TMDB overview backfill job finished successfully.");
            future.complete(null);
        } catch (Exception ex) {
            log.error("TMDB overview backfill job failed", ex);
            future.completeExceptionally(ex);
        }
        return future;
    }
}
