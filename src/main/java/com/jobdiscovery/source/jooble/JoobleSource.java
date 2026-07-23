package com.jobdiscovery.source.jooble;

import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.source.JobSource;
import com.jobdiscovery.source.jooble.dto.JoobleResponse;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Jooble as a {@link JobSource}. */
@Component
@Order(2)
public class JoobleSource implements JobSource {

    private final JoobleClient client;
    private final JoobleJobMapper mapper;
    private final JoobleProperties properties;

    public JoobleSource(JoobleClient client, JoobleJobMapper mapper, JoobleProperties properties) {
        this.client = client;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public String name() {
        return JoobleJobMapper.SOURCE;
    }

    @Override
    public List<JobListing> fetchJobs(String keywords, String location) {
        JoobleResponse response = client.search(keywords, location);

        // Jooble's location matching is coarse for some markets (e.g. Indian city
        // names return nothing while the country does). If the specific location
        // yields no results, retry once with the configured broader fallback.
        if (isEmpty(response)
                && StringUtils.hasText(properties.fallbackLocation())
                && !properties.fallbackLocation().equalsIgnoreCase(location)) {
            response = client.search(keywords, properties.fallbackLocation());
        }

        if (isEmpty(response)) {
            return List.of();
        }
        return response.jobs().stream().map(mapper::toJobListing).toList();
    }

    private boolean isEmpty(JoobleResponse response) {
        return response == null || response.jobs() == null || response.jobs().isEmpty();
    }
}
