package com.jobdiscovery.fetch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuration for the scheduled daily fetch. */
@ConfigurationProperties(prefix = "fetch.schedule")
public record FetchScheduleProperties(
        @DefaultValue("true") boolean enabled,
        // Spring cron: second minute hour day-of-month month day-of-week.
        @DefaultValue("0 0 6 * * *") String cron,
        @DefaultValue("Asia/Kolkata") String zone,
        @DefaultValue("java developer") String keywords,
        @DefaultValue("bangalore") String location) {
}
