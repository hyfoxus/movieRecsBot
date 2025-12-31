package com.gnemirko.movieRecsBot.normalizer;

import com.gnemirko.movieRecsBot.service.UserLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextNormalizerTest {

    @Mock
    private NormalizerClient normalizerClient;

    private TextNormalizer textNormalizer;

    @BeforeEach
    void setUp() {
        textNormalizer = new TextNormalizer(normalizerClient);
    }

    @Test
    void normalizeToEnglishReturnsSanitizedResponse() {
        when(normalizerClient.normalize(any(), any())).thenReturn(
                new NormalizationResponse(
                        "<<<привет>>>",
                        "<<<Movie for night>>>",
                        "ru",
                        true,
                        "Translated from ru to en"
                )
        );

        NormalizedInput input = textNormalizer.normalizeToEnglish("Фильм на вечер");

        assertThat(input.normalizedText()).isEqualTo("Movie for night");
        assertThat(input.language().isoCode()).isEqualTo("ru");
        verify(normalizerClient).normalize("Фильм на вечер", "en");
    }

    @Test
    void normalizeToEnglishFallsBackWhenServiceUnavailable() {
        when(normalizerClient.normalize(any(), any())).thenReturn(null);

        NormalizedInput input = textNormalizer.normalizeToEnglish("mixed язык");

        assertThat(input.normalizedText()).isEqualTo("mixed язык");
        assertThat(input.language().isoCode()).isEqualTo("en");
        verify(normalizerClient).normalize("mixed язык", "en");
        verifyNoMoreInteractions(normalizerClient);
    }
}
