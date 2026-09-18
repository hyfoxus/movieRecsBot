package com.gnemirko.imdbvec.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorIndexServiceTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    private VectorIndexService service() throws Exception {
        VectorIndexService service = new VectorIndexService();
        Field field = VectorIndexService.class.getDeclaredField("em");
        field.setAccessible(true);
        field.set(service, entityManager);
        return service;
    }

    @Test
    void ensureHnswIndexCreatesExtensionAndConditionalIndex() throws Exception {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);

        service().ensureHnswIndex();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(2)).createNativeQuery(sqlCaptor.capture());
        verify(query, times(2)).executeUpdate();

        assertThat(sqlCaptor.getAllValues().get(0)).contains("CREATE EXTENSION IF NOT EXISTS vector");
        assertThat(sqlCaptor.getAllValues().get(1))
                .contains("movie_embedding_hnsw")
                .contains("hnsw (embedding vector_cosine_ops)");
    }
}
