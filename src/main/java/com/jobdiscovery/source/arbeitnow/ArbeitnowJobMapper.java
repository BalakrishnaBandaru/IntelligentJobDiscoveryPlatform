package com.jobdiscovery.source.arbeitnow;

import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.source.arbeitnow.dto.ArbeitnowJob;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Maps an Arbeitnow job into our normalised {@link JobListing}. */
@Component
public class ArbeitnowJobMapper {

    public static final String SOURCE = "ARBEITNOW";

    public JobListing toJobListing(ArbeitnowJob job) {
        return new JobListing(
                job.title(),
                job.companyName(),
                job.location(),
                job.description(),
                job.url(),
                SOURCE,
                job.createdAt() != null ? Instant.ofEpochSecond(job.createdAt()) : null,
                Instant.now());
    }
}
