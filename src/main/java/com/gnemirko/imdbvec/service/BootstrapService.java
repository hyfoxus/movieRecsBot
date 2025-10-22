package com.gnemirko.imdbvec.service;

import com.gnemirko.imdbvec.importer.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class BootstrapService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    private final ImportService importer;
    private final EmbeddingService embeddings;
    private final VectorIndexService indexer;

    public BootstrapService(ImportService importer,
                            EmbeddingService embeddings,
                            VectorIndexService indexer) {
        this.importer = importer;
        this.embeddings = embeddings;
        this.indexer = indexer;
    }

    /**
     * Kick off a full refresh asynchronously.
     */
    @Async
    public CompletableFuture<Void> runFullBootstrap(boolean rebuildIndex) {
        var future = new CompletableFuture<Void>();
        try {
            log.info("Bootstrap job started (rebuildIndex={})", rebuildIndex);
            importer.runFullImport();
            embeddings.backfillEmbeddings();
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
}
