package com.jobdiscovery.fetch;

import com.jobdiscovery.job.IngestionResult;
import com.jobdiscovery.job.JobIngestionService;
import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.source.JobSource;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs a fetch across every registered {@link JobSource}, ingesting each
 * source's results through the shared de-dup pipeline. Used by both the manual
 * endpoint ({@code POST /api/fetch}) and the scheduled daily job, so they behave
 * identically.
 */
@Service
public class JobFetchService {

    private static final Logger log = LoggerFactory.getLogger(JobFetchService.class);

    private final List<JobSource> sources;
    private final JobIngestionService ingestionService;

    public JobFetchService(List<JobSource> sources, JobIngestionService ingestionService) {
        this.sources = sources;
        this.ingestionService = ingestionService;
    }

    public FetchSummary fetchAll(String keywords, String location) {
        List<SourceOutcome> outcomes = new ArrayList<>();
        for (JobSource source : sources) {
            try {
                List<JobListing> jobs = source.fetchJobs(keywords, location);
                IngestionResult r = ingestionService.ingest(jobs);
                outcomes.add(new SourceOutcome(source.name(), r.fetched(), r.saved(),
                        r.duplicates(), r.invalid(), null));
            } catch (Exception e) {
                // One source failing must not abort the others.
                log.warn("Source {} failed: {}", source.name(), e.getMessage());
                outcomes.add(new SourceOutcome(source.name(), 0, 0, 0, 0, e.getMessage()));
            }
        }
        int totalFetched = outcomes.stream().mapToInt(SourceOutcome::fetched).sum();
        int totalSaved = outcomes.stream().mapToInt(SourceOutcome::saved).sum();
        int totalDuplicates = outcomes.stream().mapToInt(SourceOutcome::duplicates).sum();
        return new FetchSummary(keywords, location, outcomes, totalFetched, totalSaved, totalDuplicates);
    }
}
