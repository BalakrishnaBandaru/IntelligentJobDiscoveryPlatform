package com.jobdiscovery.explain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body from Ollama's {@code POST /api/chat}, narrowed to what we use.
 *
 * @param doneReason why generation stopped. {@code "length"} means the answer
 *                   was cut off at {@code num_predict} rather than finishing
 * @param error      Ollama reports some failures as HTTP 200 with this set —
 *                   most usefully "model not found", which is what you get
 *                   before the model has been pulled
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponse(
        String model,
        Message message,
        boolean done,
        @JsonProperty("done_reason") String doneReason,
        String error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {
    }

    /** The answer text, or an empty string when there is none. */
    public String text() {
        return message == null || message.content() == null ? "" : message.content().trim();
    }
}
