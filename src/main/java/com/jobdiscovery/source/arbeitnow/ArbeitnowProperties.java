package com.jobdiscovery.source.arbeitnow;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuration for the Arbeitnow open API (no auth). */
@ConfigurationProperties(prefix = "arbeitnow")
public record ArbeitnowProperties(
        @DefaultValue("https://www.arbeitnow.com/api") String baseUrl,
        // Disabled by default — Arbeitnow's open API is Germany/EU-centric and
        // supports no keyword/location filtering, so it is not useful for a
        // Java/India search. Set ARBEITNOW_ENABLED=true to re-activate the source.
        @DefaultValue("false") boolean enabled) {
}
