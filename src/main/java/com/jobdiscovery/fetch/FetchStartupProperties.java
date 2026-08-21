package com.jobdiscovery.fetch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the catch-up fetch that runs when the app starts.
 *
 * @param enabled     off in the test configuration, where a fetch would call the
 *                    live job boards and spend real API quota
 * @param maxAgeHours how stale the last fetch attempt must be before starting
 *                    the app triggers another. 12 means at most two fetches a
 *                    day from restarts, which sits comfortably inside the free
 *                    tiers while still giving fresh data on a morning start
 */
@ConfigurationProperties(prefix = "fetch.startup")
public record FetchStartupProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("12") int maxAgeHours) {
}
