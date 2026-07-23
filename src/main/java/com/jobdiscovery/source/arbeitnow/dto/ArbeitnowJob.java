package com.jobdiscovery.source.arbeitnow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single job from the Arbeitnow board. {@code created_at} is a Unix epoch
 * (seconds). Only the fields we consume are declared.
 */
public record ArbeitnowJob(
        String title,
        @JsonProperty("company_name") String companyName,
        String location,
        String description,
        String url,
        @JsonProperty("created_at") Long createdAt) {
}
