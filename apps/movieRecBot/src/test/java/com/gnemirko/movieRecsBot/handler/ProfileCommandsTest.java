package com.gnemirko.movieRecsBot.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileCommandsTest {

    private Locale originalLocale;

    @BeforeEach
    void saveLocale() {
        originalLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void matchHandlesLocaleSensitiveCharacters() {
        Locale.setDefault(new Locale("tr"));

        ProfileCommands command = ProfileCommands.match("/HELP show me options");

        assertThat(command).isEqualTo(ProfileCommands.HELP);
    }

    @Test
    void matchReturnsNullWhenUnknown() {
        ProfileCommands command = ProfileCommands.match("no command");

        assertThat(command).isNull();
    }
}
