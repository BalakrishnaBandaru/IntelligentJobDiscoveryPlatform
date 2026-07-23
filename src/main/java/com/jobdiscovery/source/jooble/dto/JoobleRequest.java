package com.jobdiscovery.source.jooble.dto;

/** Request body for Jooble's search endpoint. */
public record JoobleRequest(String keywords, String location) {
}
