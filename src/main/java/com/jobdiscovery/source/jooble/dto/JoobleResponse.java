package com.jobdiscovery.source.jooble.dto;

import java.util.List;

/** Top-level Jooble search response. */
public record JoobleResponse(int totalCount, List<JoobleJob> jobs) {
}
