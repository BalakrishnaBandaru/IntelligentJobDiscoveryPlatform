package com.jobdiscovery.explain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request body for {@code POST /v1/messages}.
 *
 * <p>Anthropic uses snake_case on the wire, so the fields that differ from our
 * camelCase are mapped explicitly — the same convention the Adzuna DTOs use.
 *
 * @param outputConfig controls how hard the model thinks. Thinking is on by
 *                     default on this model family; the lever is effort, not
 *                     switching thinking off, which has its own failure modes
 * @param fallbacks    server-side refusal routing. If a safety classifier
 *                     declines the request, the API retries on another model
 *                     instead of simply stopping
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClaudeMessageRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        String system,
        List<Message> messages,
        @JsonProperty("output_config") OutputConfig outputConfig,
        String fallbacks) {

    /** One turn of the conversation. We only ever send a single user turn. */
    public record Message(String role, String content) {

        public static Message user(String content) {
            return new Message("user", content);
        }
    }

    /** {@code effort} is nested here, not a top-level request field. */
    public record OutputConfig(String effort) {
    }
}
