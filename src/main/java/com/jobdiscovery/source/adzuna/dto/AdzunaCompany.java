package com.jobdiscovery.source.adzuna.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Company block within an Adzuna result. */
public record AdzunaCompany(@JsonProperty("display_name") String displayName) {
}
