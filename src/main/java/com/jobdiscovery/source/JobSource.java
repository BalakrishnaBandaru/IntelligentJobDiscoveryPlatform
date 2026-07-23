package com.jobdiscovery.source;

import com.jobdiscovery.job.JobListing;
import java.util.List;

/**
 * A single external job source (Adzuna, Jooble, Arbeitnow…). Each implementation
 * calls its own API and maps the results into our normalised {@link JobListing}
 * shape. The orchestrator ({@code /api/fetch}) iterates every registered source.
 */
public interface JobSource {

    /** Stable source identifier stored on each listing, e.g. "ADZUNA". */
    String name();

    /**
     * Fetch and map jobs for the given search terms. Sources that don't support
     * server-side search (e.g. Arbeitnow's open feed) may ignore the arguments.
     */
    List<JobListing> fetchJobs(String keywords, String location);
}
