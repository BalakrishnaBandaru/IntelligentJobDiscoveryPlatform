package com.jobdiscovery.fetch;

import com.jobdiscovery.job.IngestionResult;
import com.jobdiscovery.job.JobIngestionService;
import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.source.JobSource;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Orchestrates a fetch across ALL registered {@link JobSource}s, ingesting each
 * source's results through the shared de-dup pipeline and returning a per-source
 * breakdown. A single source failing (e.g. missing key) does not abort the rest.
 */
@RestController
public class FetchController {

    private static final Logger log = LoggerFactory.getLogger(FetchController.class);

    private final List<JobSource> sources;
    private final JobIngestionService ingestionService;

    public FetchController(List<JobSource> sources, JobIngestionService ingestionService) {
        this.sources = sources;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/api/fetch")
    public FetchSummary fetchAll(
            @RequestParam(defaultValue = "java developer") String keywords,
            @RequestParam(defaultValue = "bangalore") String location) {

        List<SourceOutcome> outcomes = new ArrayList<>();
        for (JobSource source : sources) {
            try {
                List<JobListing> jobs = source.fetchJobs(keywords, location);
                IngestionResult r = ingestionService.ingest(jobs);
                log.info("Source {}: fetched={} saved={} duplicates={} invalid={}",
                        source.name(), r.fetched(), r.saved(), r.duplicates(), r.invalid());
                outcomes.add(new SourceOutcome(source.name(), r.fetched(), r.saved(),
                        r.duplicates(), r.invalid(), null));
            } catch (Exception e) {
                log.warn("Source {} failed: {}", source.name(), e.getMessage());
                outcomes.add(new SourceOutcome(source.name(), 0, 0, 0, 0, e.getMessage()));
            }
        }

        int totalFetched = outcomes.stream().mapToInt(SourceOutcome::fetched).sum();
        int totalSaved = outcomes.stream().mapToInt(SourceOutcome::saved).sum();
        int totalDuplicates = outcomes.stream().mapToInt(SourceOutcome::duplicates).sum();
        return new FetchSummary(keywords, location, outcomes, totalFetched, totalSaved, totalDuplicates);
    }

    /** Per-source result. {@code error} is null on success. */
    public record SourceOutcome(String source, int fetched, int saved, int duplicates,
                                int invalid, String error) {
    }

    public record FetchSummary(String keywords, String location, List<SourceOutcome> sources,
                               int totalFetched, int totalSaved, int totalDuplicates) {
    }
}
