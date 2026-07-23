package com.jobdiscovery.source.adzuna;

import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.source.JobSource;
import com.jobdiscovery.source.adzuna.dto.AdzunaSearchResponse;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Adzuna as a {@link JobSource}. */
@Component
@Order(1)
public class AdzunaSource implements JobSource {

    private final AdzunaClient client;
    private final AdzunaJobMapper mapper;

    public AdzunaSource(AdzunaClient client, AdzunaJobMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return AdzunaJobMapper.SOURCE;
    }

    @Override
    public List<JobListing> fetchJobs(String keywords, String location) {
        AdzunaSearchResponse response = client.search(keywords, location, 1);
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream().map(mapper::toJobListing).toList();
    }

    /** Adzuna-specific: raw JSON passthrough for the inspection endpoint. */
    public String rawSearch(String what, String where, int page) {
        return client.searchRaw(what, where, page);
    }
}
