package com.gnemirko.movieRecsBot.normalizer;

import com.gnemirko.movieRecsBot.service.UserLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextNormalizerTest {

    @Mock
    private NormalizerClient normalizerClient;

    @InjectMocks
    private TextNormalizer textNormalizer;

    @BeforeEach
    void setUp() {
        // Mockito injects mocks automatically
    }

    @Test
    void translateFromEnglishPreservesTitles() {
        String english = """
                Here are films:
                1. <b>Ocean's Eleven</b> — Stylish heist movie.
                """.trim();

        when(normalizerClient.normalize(anyString(), eq("ru")))
                .thenReturn(new NormalizationResponse(
                        english,
                        """
                                Здесь фильмы:
                                1. <b>__MOVIE_TITLE_0__</b> — Стильный фильм о ограблении.
                                """.trim(),
                        "ru",
                        true,
                        "notes"
                ));

        String translated = textNormalizer.translateFromEnglish(english, UserLanguage.fromIsoCode("ru"));

        assertThat(translated)
                .contains("Ocean's Eleven")
                .contains("Стильный фильм");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(normalizerClient).normalize(payloadCaptor.capture(), eq("ru"));
        assertThat(payloadCaptor.getValue()).contains("__MOVIE_TITLE_0__");
    }
}
