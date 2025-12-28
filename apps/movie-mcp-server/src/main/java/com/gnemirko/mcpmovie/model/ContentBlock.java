package com.gnemirko.mcpmovie.model;

public record ContentBlock(
        String type,
        String text,
        Object json
) {

    public static ContentBlock text(String value) {
        return new ContentBlock("text", value, null);
    }

    public static ContentBlock json(Object value) {
        return new ContentBlock("json", null, value);
    }
}
