package com.gnemirko.mcpmovie.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolInvocationTest {

    @Test
    void nullArgumentsDefaultToEmptyMap() {
        ToolInvocation invocation = new ToolInvocation("movie.search", null);

        assertThat(invocation.arguments()).isEmpty();
    }

    @Test
    void argumentsAreDefensivelyCopiedAndImmutable() {
        Map<String, Object> source = new HashMap<>();
        source.put("query", "noir");

        ToolInvocation invocation = new ToolInvocation("movie.search", source);
        source.put("query", "mutated after construction");

        assertThat(invocation.arguments()).containsEntry("query", "noir");
        assertThatThrownBy(() -> invocation.arguments().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
