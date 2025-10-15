package com.gnemirko.movieRecsBot.handler;


import java.util.Arrays;

public enum ProfileCommands {
    PROFILE("/profile"),
    LIKE_GENRE("/like_genre"),
    LIKE_ACTOR("/like_actor"),
    LIKE_DIRECTOR("/like_director"),
    BLOCK("/block"),
    UNBLOCK("/unblock"),
    RESET("/reset_profile"),
    HELP("/help");

    public final String cmd;

    ProfileCommands(String cmd) {
        this.cmd = cmd;
    }

    public static ProfileCommands match(String text) {
        if (text == null) return null;
        String t = text.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(c -> t.startsWith(c.cmd))
                .findFirst().orElse(null);
    }
}