package com.jobdiscovery.fetch;

import com.jobdiscovery.job.IngestionResult;
import com.jobdiscovery.job.JobIngestionService;
import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.source.JobSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private final FetchRunRepository runRepository;

    public JobFetchService(List<JobSource> sources, JobIngestionService ingestionService,
                           FetchRunRepository runRepository) {
        this.sources = sources;
        this.ingestionService = ingestionService;
        this.runRepository = runRepository;
    }

    /** How long ago the last fetch was attempted, whatever triggered it. */
    public Optional<Duration> timeSinceLastRun(Instant now) {
        return runRepository.findFirstByOrderByRanAtDesc()
                .map(run -> Duration.between(run.getRanAt(), now));
    }

    /** A manual fetch, e.g. from {@code POST /api/fetch}. */
    public FetchSummary fetchAll(String keywords, String location) {
        return fetchAll(keywords, location, FetchRun.Trigger.MANUAL);
    }

    /**
     * Runs every source and records the attempt.
     *
     * <p>The record is written whatever the outcome — including when nothing new
     * is found. That is the point: de-duplication means a successful run can
     * write no job rows at all, so without this a working pipeline and a dead
     * one look identical.
     */
    public FetchSummary fetchAll(String keywords, String location, FetchRun.Trigger trigger) {
        FetchRun run = new FetchRun(Instant.now(), trigger, keywords, location);
        try {
            FetchSummary summary = runSources(keywords, location);
            run.recordSuccess(summary);
            return summary;
        } catch (RuntimeException e) {
            run.recordFailure(e.getMessage());
            throw e;
        } finally {
            // Recorded even on failure, so a run that keeps blowing up does not
            // look like a run that never happened.
            runRepository.save(run);
        }
    }

    private FetchSummary runSources(String keywords, String location) {
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
