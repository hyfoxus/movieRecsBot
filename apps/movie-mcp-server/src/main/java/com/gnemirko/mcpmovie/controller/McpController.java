package com.gnemirko.mcpmovie.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.mcpmovie.config.MovieMcpProperties;
import com.gnemirko.mcpmovie.model.ContentBlock;
import com.gnemirko.mcpmovie.model.McpManifest;
import com.gnemirko.mcpmovie.model.McpToolResponse;
import com.gnemirko.mcpmovie.model.MovieContext;
import com.gnemirko.mcpmovie.model.MovieSearchRequest;
import com.gnemirko.mcpmovie.model.ResourcePointer;
import com.gnemirko.mcpmovie.model.ResourceQuery;
import com.gnemirko.mcpmovie.model.ResourceQueryResponse;
import com.gnemirko.mcpmovie.model.ResourceResult;
import com.gnemirko.mcpmovie.model.ToolInvocation;
import com.gnemirko.mcpmovie.service.MovieSearchService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Validated
@RestController
public class McpController {

    private static final String TOOL_NAME = "movie.search";
    private static final String MOVIE_URI_PREFIX = "imdb://movie/";

    private final MovieSearchService movieService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final MovieMcpProperties properties;

    public McpController(MovieSearchService movieService,
                         ObjectMapper objectMapper,
                         Validator validator,
                         MovieMcpProperties properties) {
        this.movieService = movieService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.properties = properties;
    }

    @GetMapping("/.well-known/mcp.json")
    public McpManifest manifest() {
        return new McpManifest(
                properties.name(),
                properties.version(),
                properties.description(),
                List.of(new McpManifest.McpTool(
                        TOOL_NAME,
                        "Vector-based movie search over the IMDb subset.",
                        buildSearchSchema()
                )),
                List.of(new McpManifest.McpResource(
                        MOVIE_URI_PREFIX + "{tconst}",
                        "Movie metadata",
                        "Returns metadata for a specific IMDb title."
                ))
        );
    }

    @PostMapping("/mcp/v1/tools")
    public McpToolResponse invokeTool(@Valid @RequestBody ToolInvocation invocation) {
        if (!TOOL_NAME.equals(invocation.name())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tool not found");
        }
        MovieSearchRequest request = convertArguments(invocation.arguments());
        List<MovieContext> results = movieService.search(request);
        List<ContentBlock> content = List.of(
                ContentBlock.text(String.format(
                        Locale.ROOT,
                        "Top %d matches for query '%s'.",
                        results.size(),
                        request.query()
                )),
                ContentBlock.json(results)
        );
        return new McpToolResponse(content);
    }

    @PostMapping("/mcp/v1/resources/query")
    public ResourceQueryResponse queryResources(@Valid @RequestBody ResourceQuery query) {
        List<ResourceResult> results = new ArrayList<>();
        for (ResourcePointer pointer : query.resources()) {
            String uri = pointer.uri();
            if (!uri.startsWith(MOVIE_URI_PREFIX)) {
                results.add(new ResourceResult(
                        uri,
                        List.of(ContentBlock.text("Unsupported resource"))
                ));
                continue;
            }
            String tconst = uri.substring(MOVIE_URI_PREFIX.length());
            if (tconst.isBlank()) {
                results.add(new ResourceResult(
                        uri,
                        List.of(ContentBlock.text("Missing IMDb identifier"))
                ));
                continue;
            }
            MovieContext movie = movieService.fetchByTconst(tconst).orElse(null);
            if (movie == null) {
                results.add(new ResourceResult(
                        uri,
                        List.of(ContentBlock.text("Movie " + tconst + " not found."))
                ));
                continue;
            }
            results.add(new ResourceResult(
                    uri,
                    List.of(
                            ContentBlock.text("Metadata for " + movie.title()),
                            ContentBlock.json(movie)
                    )
            ));
        }
        return new ResourceQueryResponse(results);
    }

    private MovieSearchRequest convertArguments(Map<String, Object> arguments) {
        MovieSearchRequest request = objectMapper.convertValue(arguments, MovieSearchRequest.class);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return request;
    }

    private Map<String, Object> buildSearchSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of(
                "type", "string",
                "description", "Free-form text that describes the movie the user wants."
        ));
        properties.put("limit", Map.of(
                "type", "integer",
                "minimum", 1,
                "maximum", this.properties.maxResults(),
                "description", "Maximum number of matches to return."
        ));
        properties.put("fromYear", Map.of(
                "type", "integer",
                "minimum", 1888,
                "description", "Only return titles released on or after this year."
        ));
        properties.put("toYear", Map.of(
                "type", "integer",
                "minimum", 1888,
                "description", "Only return titles released on or before this year."
        ));
        properties.put("runtimeMinutes", Map.of(
                "type", "integer",
                "minimum", 1,
                "description", "Maximum runtime in minutes."
        ));
        properties.put("minRating", Map.of(
                "type", "number",
                "minimum", 0,
                "maximum", 10,
                "description", "Only return movies with rating >= minRating."
        ));
        Map<String, Object> genreArray = Map.of(
                "type", "array",
                "items", Map.of("type", "string")
        );
        properties.put("includeGenres", genreArray);
        properties.put("excludeGenres", genreArray);
        properties.put("actors", Map.of(
                "type", "array",
                "description", "Names of actors that must appear in the movie.",
                "items", Map.of("type", "string")
        ));

        return Map.of(
                "type", "object",
                "required", List.of("query"),
                "properties", properties
        );
    }
}
