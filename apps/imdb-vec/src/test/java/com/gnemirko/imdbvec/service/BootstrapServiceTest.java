package com.gnemirko.imdbvec.service;

import com.gnemirko.imdbvec.importer.ImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BootstrapServiceTest {

    @Mock
    private ImportService importer;
    @Mock
    private MovieOverviewService overviewService;
    @Mock
    private EmbeddingService embeddings;
    @Mock
    private VectorIndexService indexer;

    private BootstrapService serviceSkippingEmbeddingErrors() {
        return new BootstrapService(importer, overviewService, embeddings, indexer, true);
    }

    private BootstrapService serviceFailingOnEmbeddingErrors() {
        return new BootstrapService(importer, overviewService, embeddings, indexer, false);
    }

    @Test
    void runFullBootstrapRunsStepsInOrderAndRebuildsIndexWhenRequested() throws Exception {
        CompletableFuture<Void> result = serviceSkippingEmbeddingErrors().runFullBootstrap(true);
        result.get();

        InOrder order = inOrder(importer, overviewService, embeddings, indexer);
        order.verify(importer).runFullImport();
        order.verify(overviewService).backfillOverviews();
        order.verify(embeddings).backfillEmbeddings();
        order.verify(indexer).ensureHnswIndex();
        assertThat(result).isCompleted();
    }

    @Test
    void runFullBootstrapSkipsIndexRebuildWhenNotRequested() throws Exception {
        serviceSkippingEmbeddingErrors().runFullBootstrap(false).get();

        verify(indexer, never()).ensureHnswIndex();
    }

    @Test
    void runFullBootstrapContinuesWhenOverviewBackfillFails() throws Exception {
        doThrow(new RuntimeException("tmdb down")).when(overviewService).backfillOverviews();

        CompletableFuture<Void> result = serviceSkippingEmbeddingErrors().runFullBootstrap(true);
        result.get();

        verify(embeddings).backfillEmbeddings();
        verify(indexer).ensureHnswIndex();
        assertThat(result).isCompleted();
    }

    @Test
    void runFullBootstrapSkipsEmbeddingFailureWhenConfiguredToSkip() throws Exception {
        doThrow(new RuntimeException("ollama down")).when(embeddings).backfillEmbeddings();

        CompletableFuture<Void> result = serviceSkippingEmbeddingErrors().runFullBootstrap(true);
        result.get();

        verify(indexer).ensureHnswIndex();
        assertThat(result).isCompleted();
    }

    @Test
    void runFullBootstrapPropagatesEmbeddingFailureWhenSkipDisabled() {
        doThrow(new RuntimeException("ollama down")).when(embeddings).backfillEmbeddings();

        CompletableFuture<Void> result = serviceFailingOnEmbeddingErrors().runFullBootstrap(true);

        assertThat(result).isCompletedExceptionally();
        assertThatThrownBy(result::get)
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseMessage("ollama down");
        verify(indexer, never()).ensureHnswIndex();
    }

    @Test
    void runFullBootstrapCompletesExceptionallyWhenImportFails() throws Exception {
        doThrow(new RuntimeException("download failed")).when(importer).runFullImport();

        CompletableFuture<Void> result = serviceSkippingEmbeddingErrors().runFullBootstrap(true);

        assertThat(result).isCompletedExceptionally();
        verifyNoInteractions(overviewService);
        verifyNoInteractions(embeddings);
        verifyNoInteractions(indexer);
    }

    @Test
    void runTmdbOverviewBackfillDelegatesWithGivenParameters() throws Exception {
        CompletableFuture<Void> result = serviceSkippingEmbeddingErrors().runTmdbOverviewBackfill(500L, 25);
        result.get();

        verify(overviewService).backfillOverviews(500L, 25);
        assertThat(result).isCompleted();
    }

    @Test
    void runTmdbOverviewBackfillCompletesExceptionallyOnFailure() {
        doThrow(new RuntimeException("tmdb error")).when(overviewService).backfillOverviews(null, null);

        CompletableFuture<Void> result = serviceSkippingEmbeddingErrors().runTmdbOverviewBackfill(null, null);

        assertThat(result).isCompletedExceptionally();
    }
}
