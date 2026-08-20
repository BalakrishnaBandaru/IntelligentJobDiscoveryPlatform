package com.jobdiscovery.explain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response body from {@code POST /v1/messages}, narrowed to what we use.
 *
 * <p>Unknown fields are ignored on purpose: the API gains fields over time and
 * a new one must not start failing our deserialisation.
 *
 * @param stopReason why generation ended. {@code "refusal"} is the one that
 *                   matters — it arrives as HTTP 200 with no usable content, so
 *                   it has to be checked <i>before</i> reading {@link #content}
 * @param content    a list of blocks. Only {@code text} blocks are ours to
 *                   read; thinking blocks may also be present
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeMessageResponse(
        String id,
        String model,
        List<ContentBlock> content,
        @JsonProperty("stop_reason") String stopReason,
        @JsonProperty("stop_details") StopDetails stopDetails,
        Usage usage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(String type, String text) {
    }

    /** Populated only when {@link #stopReason} is {@code "refusal"}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StopDetails(String type, String category, String explanation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens) {
    }

    /** The visible answer: every text block joined, thinking blocks skipped. */
    public String text() {
        if (content == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : content) {
            if ("text".equals(block.type()) && block.text() != null) {
                sb.append(block.text());
            }
        }
        return sb.toString().trim();
    }
}
