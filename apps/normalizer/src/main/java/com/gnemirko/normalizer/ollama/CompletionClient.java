package com.gnemirko.normalizer.ollama;

public interface CompletionClient {
    String complete(String model, String prompt);
}
