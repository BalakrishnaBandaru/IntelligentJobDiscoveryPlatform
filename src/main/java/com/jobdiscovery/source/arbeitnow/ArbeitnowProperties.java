package com.jobdiscovery.source.arbeitnow;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuration for the Arbeitnow open API (no auth). */
@ConfigurationProperties(prefix = "arbeitnow")
public record ArbeitnowProperties(
        @DefaultValue("https://www.arbeitnow.com/api") String baseUrl) {
}
