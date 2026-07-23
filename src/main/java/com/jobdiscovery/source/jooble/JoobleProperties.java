package com.jobdiscovery.source.jooble;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the Jooble API client. The API key is read from an
 * environment variable (see .env) and is part of the request URL path.
 */
@ConfigurationProperties(prefix = "jooble")
public record JoobleProperties(
        @DefaultValue("https://jooble.org/api") String baseUrl,
        String apiKey,
        // Jooble's location matching is coarse in some markets (Indian city names
        // return nothing, but the country does). If a specific location yields no
        // results, we retry with this broader fallback.
        @DefaultValue("India") String fallbackLocation) {
}
