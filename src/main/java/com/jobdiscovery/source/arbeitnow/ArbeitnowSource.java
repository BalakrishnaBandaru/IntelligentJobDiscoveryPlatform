package com.jobdiscovery.source.arbeitnow;

import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.source.JobSource;
import com.jobdiscovery.source.arbeitnow.dto.ArbeitnowResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Arbeitnow as a {@link JobSource}.
 *
 * <p><b>Integrated but NOT an active source for the current use case.</b>
 * Arbeitnow's free API is a Germany/EU-centric job-board <i>feed</i>. It was
 * verified empirically to support NO keyword or location filtering — the params
 * {@code search / q / keyword / tag / tags / location / remote} are all ignored
 * (only pagination and {@code visa_sponsorship} are honoured). For a
 * Java/Bangalore/India search it returns almost entirely irrelevant
 * German-language postings, so it is <b>disabled by default</b>
 * ({@code arbeitnow.enabled=false}).
 *
 * <p>The client, mapper and DTOs are deliberately kept in place — set
 * {@code ARBEITNOW_ENABLED=true} to re-activate this source for a future
 * European job search.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "arbeitnow.enabled", havingValue = "true")
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
