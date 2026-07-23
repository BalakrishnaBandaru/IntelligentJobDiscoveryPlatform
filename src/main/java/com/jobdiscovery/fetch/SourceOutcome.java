package com.jobdiscovery.fetch;

/** Per-source result of a fetch. {@code error} is null on success. */
public record SourceOutcome(String source, int fetched, int saved, int duplicates,
                            int invalid, String error) {
}
