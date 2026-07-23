package com.jobdiscovery.source.jooble;

import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.source.jooble.dto.JoobleJob;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

/** Maps a Jooble job into our normalised {@link JobListing}. */
@Component
public class JoobleJobMapper {

    public static final String SOURCE = "JOOBLE";

    public JobListing toJobListing(JoobleJob job) {
        return new JobListing(
                job.title(),
                job.company(),
                job.location(),
                job.snippet(),
                job.link(),
                SOURCE,
                parseTimestamp(job.updated()),
                Instant.now());
    }

    /**
     * Jooble's "updated" is usually an ISO date-time (sometimes with an offset,
     * sometimes local). Parse defensively; a bad/absent value just means no date.
     */
    private Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // not offset-qualified — try local
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
