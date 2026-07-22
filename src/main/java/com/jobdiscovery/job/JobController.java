package com.jobdiscovery.job;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoints for inspecting what has been persisted. Handy during
 * Phase 1 to confirm listings were stored and to click through their apply URLs.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobListingRepository repository;

    public JobController(JobListingRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<JobListing> all() {
        return repository.findAll();
    }

    @GetMapping("/count")
    public long count() {
        return repository.count();
    }
}
