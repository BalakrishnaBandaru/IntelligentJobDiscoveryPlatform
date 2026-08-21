package com.jobdiscovery.source.adzuna;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.jobdiscovery.job.IngestionResult;
import com.jobdiscovery.job.JobIngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adzuna-specific endpoints.
 *
 * <ul>
 *   <li>{@code GET  /api/adzuna/search} — raw Adzuna JSON, for inspecting the
 *       response structure. Does NOT persist anything.</li>
 *   <li>{@code POST /api/adzuna/import} — fetch + map + de-dup + persist (Adzuna
 *       only). To fetch from every source at once, use {@code POST /api/fetch}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/adzuna")
@Tag(name = "Fetch")
public class AdzunaController {

    private final AdzunaSource adzunaSource;
    private final JobIngestionService ingestionService;

    public AdzunaController(AdzunaSource adzunaSource, JobIngestionService ingestionService) {
        this.adzunaSource = adzunaSource;
        this.ingestionService = ingestionService;
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> search(
            @RequestParam(defaultValue = "java developer") String what,
            @RequestParam(defaultValue = "bangalore") String where,
            @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(adzunaSource.rawSearch(what, where, page));
    }

    @PostMapping("/import")
    public IngestionResult importJobs(
            @RequestParam(defaultValue = "java developer") String what,
            @RequestParam(defaultValue = "bangalore") String where) {
        return ingestionService.ingest(adzunaSource.fetchJobs(what, where));
    }
}
