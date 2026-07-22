package com.jobdiscovery.source.adzuna;

import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.source.adzuna.dto.AdzunaResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

/** Maps an Adzuna result into our normalised {@link JobListing} entity. */
@Component
public class AdzunaJobMapper {

    /** Marker stored on every row so we know which source it came from. */
    public static final String SOURCE = "ADZUNA";

    public JobListing toJobListing(AdzunaResult result) {
        return new JobListing(
                result.title(),
                result.company() != null ? result.company().displayName() : null,
                result.location() != null ? result.location().displayName() : null,
                result.description(),
                result.redirectUrl(),
                SOURCE,
                parseTimestamp(result.created()),
                Instant.now());
    }

    /** Adzuna returns ISO-8601 timestamps like {@code 2026-07-20T09:15:00Z}. */
    private Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            return null; // Don't fail the whole import over one odd date.
        }
    }
}
