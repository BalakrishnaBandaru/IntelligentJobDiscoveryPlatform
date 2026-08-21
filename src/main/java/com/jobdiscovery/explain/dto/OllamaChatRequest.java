package com.jobdiscovery.explain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request body for Ollama's {@code POST /api/chat}.
 *
 * @param stream  always false — we want one complete answer, not a token feed
 * @param options generation knobs. {@code num_predict} is Ollama's name for an
 *                output-token ceiling, and a low temperature keeps the model
 *                close to the evidence instead of embellishing it
 */
public record OllamaChatRequest(
        String model,
        List<Message> messages,
        boolean stream,
        Options options) {

    public record Message(String role, String content) {

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }
    }

    public record Options(
            double temperature,
            @JsonProperty("num_predict") int numPredict) {
    }
}
