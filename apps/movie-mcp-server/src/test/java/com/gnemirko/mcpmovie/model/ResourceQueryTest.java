package com.gnemirko.mcpmovie.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceQueryTest {

    @Test
    void nullResourcesDefaultToEmptyList() {
        ResourceQuery query = new ResourceQuery(null);

        assertThat(query.resources()).isEmpty();
    }

    @Test
    void resourcesAreDefensivelyCopiedAndImmutable() {
        ResourceQuery query = new ResourceQuery(List.of(new ResourcePointer("imdb://movie/tt1")));

        assertThatThrownBy(() -> query.resources().add(new ResourcePointer("imdb://movie/tt2")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
