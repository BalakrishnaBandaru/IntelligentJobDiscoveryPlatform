package com.jobdiscovery.source.adzuna;

import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.job.JobListingRepository;
import com.jobdiscovery.source.adzuna.dto.AdzunaSearchResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates a single Adzuna fetch: call the API, map results to entities and
 * persist them.
 *
 * <p>NOTE: Phase 1 has no de-duplication yet, so running an import twice will
 * create duplicate rows. De-duplication is added in Phase 2.
 */
@Service
public class AdzunaImportService {

    private final AdzunaClient client;
    private final AdzunaJobMapper mapper;
    private final JobListingRepository repository;

    public AdzunaImportService(AdzunaClient client, AdzunaJobMapper mapper,
                               JobListingRepository repository) {
        this.client = client;
        this.mapper = mapper;
        this.repository = repository;
    }

    @Transactional
    public List<JobListing> importJobs(String what, String where, int page) {
        AdzunaSearchResponse response = client.search(what, where, page);
        if (response == null || response.results() == null) {
            return List.of();
        }
        List<JobListing> listings = response.results().stream()
                .map(mapper::toJobListing)
                .toList();
        return repository.saveAll(listings);
    }

    /** Pass-through to the raw JSON, for the inspection endpoint. */
    public String fetchRaw(String what, String where, int page) {
        return client.searchRaw(what, where, page);
    }
}
