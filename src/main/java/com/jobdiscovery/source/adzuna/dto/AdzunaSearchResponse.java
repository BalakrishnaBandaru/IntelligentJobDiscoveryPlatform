package com.jobdiscovery.source.adzuna.dto;

import java.util.List;

/**
 * Top-level Adzuna search response. Only the fields we consume are declared;
 * Jackson ignores unknown properties by default (Spring Boot disables
 * FAIL_ON_UNKNOWN_PROPERTIES).
 */
public record AdzunaSearchResponse(long count, List<AdzunaResult> results) {
}
