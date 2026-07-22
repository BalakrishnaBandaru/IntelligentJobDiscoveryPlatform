package com.jobdiscovery.source.adzuna.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Location block within an Adzuna result. */
public record AdzunaLocation(@JsonProperty("display_name") String displayName) {
}
