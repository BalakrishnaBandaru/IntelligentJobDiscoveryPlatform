package com.jobdiscovery.application;

import java.time.Instant;

/**
 * An application together with enough of the listing to be readable on its own.
 *
 * <p>The tracker's list view is useless showing bare job ids, and making the
 * caller fetch each listing separately would be an N+1 in the client instead of
 * the server. The listing fields are copied at read time rather than joined into
 * the entity, so an application never drags a full JobListing — description
 * included — into memory.
 */
public record ApplicationView(
        Long id,
        Long jobId,
        String title,
        String company,
        String location,
        String applyUrl,
        ApplicationStatus status,
        Instant appliedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {
}
