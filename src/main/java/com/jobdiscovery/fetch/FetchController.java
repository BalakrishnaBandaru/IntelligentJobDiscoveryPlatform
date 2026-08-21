package com.jobdiscovery.fetch;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for a multi-source fetch across all active sources. Delegates
 * to {@link JobFetchService} — the same pipeline the scheduled job uses.
 */
@RestController
public class FetchController {

    private final JobFetchService fetchService;
    private final FetchRunRepository runRepository;

    public FetchController(JobFetchService fetchService, FetchRunRepository runRepository) {
        this.fetchService = fetchService;
        this.runRepository = runRepository;
    }

    @PostMapping("/api/fetch")
    public FetchSummary fetchAll(
            @RequestParam(defaultValue = "java developer") String keywords,
            @RequestParam(defaultValue = "bangalore") String location) {
        return fetchService.fetchAll(keywords, location);
    }

    /**
     * The last 20 fetch attempts, newest first.
     *
     * <p>Answers the question that had no answer before: <i>is the pipeline
     * actually running?</i> The job table cannot say — de-duplication means a
     * healthy run that finds only duplicates writes nothing, so a stalled
     * pipeline and a quiet one look the same from there.
     */
    @GetMapping("/api/fetch/runs")
    public List<FetchRun> recentRuns() {
        return runRepository.findTop20ByOrderByRanAtDesc();
    }
}
