package com.jobdiscovery.source.adzuna;

import com.jobdiscovery.job.JobListing;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 1 test endpoints for the Adzuna source.
 *
 * <ul>
 *   <li>{@code GET  /api/adzuna/search} — raw Adzuna JSON, for inspecting the
 *       response structure. Does NOT persist anything.</li>
 *   <li>{@code POST /api/adzuna/import} — fetch + map + persist, returns what
 *       was saved.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/adzuna")
public class AdzunaController {

    private final AdzunaImportService importService;

    public AdzunaController(AdzunaImportService importService) {
        this.importService = importService;
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> search(
            @RequestParam(defaultValue = "java developer") String what,
            @RequestParam(defaultValue = "bangalore") String where,
            @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(importService.fetchRaw(what, where, page));
    }

    @PostMapping("/import")
    public ImportResult importJobs(
            @RequestParam(defaultValue = "java developer") String what,
            @RequestParam(defaultValue = "bangalore") String where,
            @RequestParam(defaultValue = "1") int page) {
        List<JobListing> saved = importService.importJobs(what, where, page);
        return new ImportResult(what, where, saved.size(), saved);
    }

    /** Self-describing wrapper around an import result. */
    public record ImportResult(String what, String where, int saved, List<JobListing> listings) {
    }
}
