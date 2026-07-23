package com.jobdiscovery.job;

/**
 * Outcome of ingesting one batch of fetched listings.
 * Invariant: {@code fetched == saved + duplicates + invalid}.
 *
 * @param fetched    total listings received from the source
 * @param saved      newly persisted (not seen before)
 * @param duplicates skipped because an identical content hash already existed
 *                   (within this batch or already in the DB)
 * @param invalid    skipped because they lacked a title or apply URL
 */
public record IngestionResult(int fetched, int saved, int duplicates, int invalid) {
}
