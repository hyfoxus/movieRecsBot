package com.gnemirko.imdbvec.importer;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The COPY/import SQL orchestration in {@link ImdbCopyLoader} talks directly to a real Postgres
 * connection (temp tables, {@code PGConnection#getCopyAPI}) and isn't practically unit-testable
 * with mocks; that logic would need a Testcontainers-backed integration test. This class covers
 * the one piece of plain Java logic that is unit-testable in isolation: the {@code ImdbFiles}
 * builder's required-field validation.
 */
class ImdbCopyLoaderFilesTest {

    private static final Path SOME_PATH = Path.of("some.tsv.gz");

    @Test
    void buildSucceedsWhenAllFourFilesAreProvided() {
        ImdbCopyLoader.ImdbFiles files = ImdbCopyLoader.ImdbFiles.builder()
                .titleBasics(SOME_PATH)
                .titleRatings(SOME_PATH)
                .nameBasics(SOME_PATH)
                .titlePrincipals(SOME_PATH)
                .build();

        assertThat(files.titleBasics()).isEqualTo(SOME_PATH);
        assertThat(files.titleRatings()).isEqualTo(SOME_PATH);
        assertThat(files.nameBasics()).isEqualTo(SOME_PATH);
        assertThat(files.titlePrincipals()).isEqualTo(SOME_PATH);
    }

    @Test
    void buildFailsWhenTitleBasicsMissing() {
        assertThatThrownBy(() -> ImdbCopyLoader.ImdbFiles.builder()
                .titleRatings(SOME_PATH)
                .nameBasics(SOME_PATH)
                .titlePrincipals(SOME_PATH)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("titleBasics");
    }

    @Test
    void buildFailsWhenTitleRatingsMissing() {
        assertThatThrownBy(() -> ImdbCopyLoader.ImdbFiles.builder()
                .titleBasics(SOME_PATH)
                .nameBasics(SOME_PATH)
                .titlePrincipals(SOME_PATH)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("titleRatings");
    }

    @Test
    void buildFailsWhenNameBasicsMissing() {
        assertThatThrownBy(() -> ImdbCopyLoader.ImdbFiles.builder()
                .titleBasics(SOME_PATH)
                .titleRatings(SOME_PATH)
                .titlePrincipals(SOME_PATH)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nameBasics");
    }

    @Test
    void buildFailsWhenTitlePrincipalsMissing() {
        assertThatThrownBy(() -> ImdbCopyLoader.ImdbFiles.builder()
                .titleBasics(SOME_PATH)
                .titleRatings(SOME_PATH)
                .nameBasics(SOME_PATH)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("titlePrincipals");
    }
}
