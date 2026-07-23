package com.jobdiscovery.source.arbeitnow;

import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.source.JobSource;
import com.jobdiscovery.source.arbeitnow.dto.ArbeitnowResponse;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Arbeitnow as a {@link JobSource}. */
@Component
@Order(3)
public class ArbeitnowSource implements JobSource {

    private final ArbeitnowClient client;
    private final ArbeitnowJobMapper mapper;

    public ArbeitnowSource(ArbeitnowClient client, ArbeitnowJobMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return ArbeitnowJobMapper.SOURCE;
    }

    /** Arbeitnow's free API is a feed, not a search — keywords/location are ignored. */
    @Override
    public List<JobListing> fetchJobs(String keywords, String location) {
        ArbeitnowResponse response = client.fetchBoard();
        if (response == null || response.data() == null) {
            return List.of();
        }
        return response.data().stream().map(mapper::toJobListing).toList();
    }
}
