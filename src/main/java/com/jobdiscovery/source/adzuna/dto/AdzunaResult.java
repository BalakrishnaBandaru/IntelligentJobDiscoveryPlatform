package com.jobdiscovery.source.adzuna.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single job result from Adzuna. Adzuna uses snake_case JSON keys, so the ones
 * that differ from our camelCase fields are mapped explicitly with
 * {@link JsonProperty}.
 */
public record AdzunaResult(
        String id,
        String title,
        String description,
        @JsonProperty("redirect_url") String redirectUrl,
        String created,
        AdzunaCompany company,
        AdzunaLocation location) {
}
