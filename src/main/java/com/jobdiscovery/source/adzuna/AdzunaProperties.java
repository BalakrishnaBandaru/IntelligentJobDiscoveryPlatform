package com.jobdiscovery.source.adzuna;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the Adzuna API client, bound from the {@code adzuna.*} keys
 * in application.yml — which in turn read environment variables, so the
 * app_id / app_key never live in source control.
 */
@ConfigurationProperties(prefix = "adzuna")
public record AdzunaProperties(
        @DefaultValue("https://api.adzuna.com/v1/api") String baseUrl,
        String appId,
        String appKey,
        @DefaultValue("in") String country,
        @DefaultValue("20") int resultsPerPage,
        // Data-freshness filter: only fetch postings from the last N days.
        // 0 disables the filter (fetches regardless of age).
        @DefaultValue("30") int maxDaysOld,
        // Result ordering: "date" (newest first), "relevance", "salary", "hybrid".
        // Blank leaves it to Adzuna's default.
        @DefaultValue("date") String sortBy) {
}
