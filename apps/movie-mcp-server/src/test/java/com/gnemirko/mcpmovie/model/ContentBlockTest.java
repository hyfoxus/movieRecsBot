package com.gnemirko.mcpmovie.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentBlockTest {

    @Test
    void textFactoryBuildsTextBlockWithNullJson() {
        ContentBlock block = ContentBlock.text("hello");

        assertThat(block.type()).isEqualTo("text");
        assertThat(block.text()).isEqualTo("hello");
        assertThat(block.json()).isNull();
    }

    @Test
    void jsonFactoryBuildsJsonBlockWithNullText() {
        Object payload = new Object();

        ContentBlock block = ContentBlock.json(payload);

        assertThat(block.type()).isEqualTo("json");
        assertThat(block.text()).isNull();
        assertThat(block.json()).isSameAs(payload);
    }
}
