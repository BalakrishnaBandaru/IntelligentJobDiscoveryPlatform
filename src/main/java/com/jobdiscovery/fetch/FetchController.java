package com.jobdiscovery.fetch;

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

    public FetchController(JobFetchService fetchService) {
        this.fetchService = fetchService;
    }

    @PostMapping("/api/fetch")
    public FetchSummary fetchAll(
            @RequestParam(defaultValue = "java developer") String keywords,
            @RequestParam(defaultValue = "bangalore") String location) {
        return fetchService.fetchAll(keywords, location);
    }
}
