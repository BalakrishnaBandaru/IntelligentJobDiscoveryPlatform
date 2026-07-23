package com.jobdiscovery.source.jooble.dto;

/**
 * A single job from Jooble. Only the fields we consume are declared; Jackson
 * ignores the rest (id, salary, source, type…). {@code snippet} is the
 * description, {@code link} is the apply URL, {@code updated} is the post date.
 */
public record JoobleJob(
        String title,
        String company,
        String location,
        String snippet,
        String link,
        String updated) {
}
