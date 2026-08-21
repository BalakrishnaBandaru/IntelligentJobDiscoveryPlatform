package com.jobdiscovery.job;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoints for inspecting what has been persisted. Handy for
 * confirming listings were stored and clicking through their apply URLs.
 */
@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Jobs")
public class JobController {

    private final JobListingRepository repository;

    public JobController(JobListingRepository repository) {
        this.repository = repository;
    }

    /** All persisted listings, or just one source's when {@code ?source=JOOBLE}. */
    @GetMapping
    public List<JobListing> all(@RequestParam(required = false) String source) {
        if (source != null && !source.isBlank()) {
            return repository.findBySourceOrderByIdDesc(source.trim().toUpperCase());
        }
        return repository.findAll();
    }

    @GetMapping("/count")
    public long count() {
        return repository.count();
    }
}
