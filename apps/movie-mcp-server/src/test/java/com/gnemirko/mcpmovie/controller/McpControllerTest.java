package com.gnemirko.mcpmovie.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.mcpmovie.config.MovieMcpProperties;
import com.gnemirko.mcpmovie.model.McpManifest;
import com.gnemirko.mcpmovie.model.McpToolResponse;
import com.gnemirko.mcpmovie.model.MovieActor;
import com.gnemirko.mcpmovie.model.MovieContext;
import com.gnemirko.mcpmovie.model.ResourcePointer;
import com.gnemirko.mcpmovie.model.ResourceQuery;
import com.gnemirko.mcpmovie.model.ResourceQueryResponse;
import com.gnemirko.mcpmovie.model.ToolInvocation;
import com.gnemirko.mcpmovie.service.MovieSearchService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpControllerTest {

    @Mock
    private MovieSearchService movieService;

    private McpController controller;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        MovieMcpProperties properties = new MovieMcpProperties("Movie MCP", "2.0.0", "desc", 15, 300);
        controller = new McpController(movieService, new ObjectMapper(), validator, properties);
    }

    private MovieContext movie(String tconst, String title) {
        return new MovieContext(tconst, title, 1999, 8.1, 12345, 0.9,
                List.of("Drama"), List.of(new MovieActor("nm1", "Actor One")), Map.of("plot", "A plot"));
    }

    @Test
    void manifestExposesSearchAndLookupToolsAndMovieResource() {
        McpManifest manifest = controller.manifest();

        assertThat(manifest.name()).isEqualTo("Movie MCP");
        assertThat(manifest.tools()).extracting(McpManifest.McpTool::name)
                .containsExactly("movie.search", "movie.lookup");
        assertThat(manifest.resources()).hasSize(1);
        assertThat(manifest.resources().get(0).uri()).isEqualTo("imdb://movie/{tconst}");
    }

    @Test
    void invokeToolDispatchesSearchAndReturnsTextAndJsonBlocks() {
        when(movieService.search(any())).thenReturn(List.of(movie("tt1", "First Movie")));
        ToolInvocation invocation = new ToolInvocation("movie.search", Map.of("query", "noir thriller"));

        McpToolResponse response = controller.invokeTool(invocation).block();

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).type()).isEqualTo("text");
        assertThat(response.content().get(0).text()).contains("Top 1 matches for query 'noir thriller'");
        assertThat(response.content().get(1).type()).isEqualTo("json");
    }

    @Test
    void invokeToolDispatchesLookupAndReturnsFoundMovie() {
        when(movieService.lookupByTitle("Heat", 1995)).thenReturn(Optional.of(movie("tt2", "Heat")));
        ToolInvocation invocation = new ToolInvocation("movie.lookup", Map.of("title", "Heat", "year", 1995));

        McpToolResponse response = controller.invokeTool(invocation).block();

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).text()).isEqualTo("Lookup result for 'Heat' (1995).");
        assertThat(response.content().get(1).json()).isNotNull();
    }

    @Test
    void invokeToolLookupReturnsNotFoundMessageWhenMissing() {
        when(movieService.lookupByTitle("Unknown Film", null)).thenReturn(Optional.empty());
        ToolInvocation invocation = new ToolInvocation("movie.lookup", Map.of("title", "Unknown Film"));

        McpToolResponse response = controller.invokeTool(invocation).block();

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).text()).isEqualTo("Movie 'Unknown Film' not found.");
    }

    @Test
    void invokeToolWithUnknownNameReturnsNotFound() {
        ToolInvocation invocation = new ToolInvocation("movie.delete", Map.of());

        assertThatThrownBy(() -> controller.invokeTool(invocation).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Tool not found");
        verifyNoInteractions(movieService);
    }

    @Test
    void invokeToolSearchWithMissingQueryThrowsConstraintViolation() {
        ToolInvocation invocation = new ToolInvocation("movie.search", Map.of("limit", 5));

        assertThatThrownBy(() -> controller.invokeTool(invocation).block())
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(movieService);
    }

    @Test
    void invokeToolLookupWithBlankTitleThrowsConstraintViolation() {
        ToolInvocation invocation = new ToolInvocation("movie.lookup", Map.of("title", "   "));

        assertThatThrownBy(() -> controller.invokeTool(invocation).block())
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(movieService);
    }

    @Test
    void queryResourcesReturnsMetadataForKnownMovie() {
        when(movieService.fetchByTconst("tt3")).thenReturn(Optional.of(movie("tt3", "Third Movie")));
        ResourceQuery query = new ResourceQuery(List.of(new ResourcePointer("imdb://movie/tt3")));

        ResourceQueryResponse response = controller.queryResources(query).block();

        assertThat(response).isNotNull();
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).content()).hasSize(2);
        assertThat(response.results().get(0).content().get(0).text()).isEqualTo("Metadata for Third Movie");
    }

    @Test
    void queryResourcesReturnsNotFoundMessageForMissingMovie() {
        when(movieService.fetchByTconst("ttMissing")).thenReturn(Optional.empty());
        ResourceQuery query = new ResourceQuery(List.of(new ResourcePointer("imdb://movie/ttMissing")));

        ResourceQueryResponse response = controller.queryResources(query).block();

        assertThat(response).isNotNull();
        assertThat(response.results().get(0).content().get(0).text()).isEqualTo("Movie ttMissing not found.");
    }

    @Test
    void queryResourcesReturnsUnsupportedForNonMovieUri() {
        ResourceQuery query = new ResourceQuery(List.of(new ResourcePointer("imdb://person/nm1")));

        ResourceQueryResponse response = controller.queryResources(query).block();

        assertThat(response).isNotNull();
        assertThat(response.results().get(0).content().get(0).text()).isEqualTo("Unsupported resource");
        verifyNoInteractions(movieService);
    }

    @Test
    void queryResourcesReturnsMissingIdentifierForBlankTconst() {
        ResourceQuery query = new ResourceQuery(List.of(new ResourcePointer("imdb://movie/")));

        ResourceQueryResponse response = controller.queryResources(query).block();

        assertThat(response).isNotNull();
        assertThat(response.results().get(0).content().get(0).text()).isEqualTo("Missing IMDb identifier");
        verifyNoInteractions(movieService);
    }
}
