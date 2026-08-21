package com.jobdiscovery.fetch;

import com.jobdiscovery.notify.DigestNotifier;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the multi-source fetch on a daily cron (configurable; default 06:00
 * Asia/Kolkata) and logs how many new jobs the run found.
 *
 * <p>Disable with {@code fetch.schedule.enabled=false}. The cron and zone are
 * bound from {@code fetch.schedule.cron} / {@code fetch.schedule.zone}.
 */
@Component
@ConditionalOnProperty(name = "fetch.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledFetchJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledFetchJob.class);

    private final JobFetchService fetchService;
    private final FetchScheduleProperties properties;
    private final DigestNotifier notifier;

    public ScheduledFetchJob(JobFetchService fetchService, FetchScheduleProperties properties,
                             DigestNotifier notifier) {
        this.fetchService = fetchService;
        this.properties = properties;
        this.notifier = notifier;
    }

    @PostConstruct
    void logSchedule() {
        log.info("Scheduled daily fetch ENABLED: cron='{}' zone='{}' keywords='{}' location='{}'",
                properties.cron(), properties.zone(), properties.keywords(), properties.location());
    }

    @Scheduled(cron = "${fetch.schedule.cron}", zone = "${fetch.schedule.zone}")
    public void runScheduledFetch() {
        log.info("Scheduled fetch starting (keywords='{}', location='{}')...",
                properties.keywords(), properties.location());

        FetchSummary summary = fetchService.fetchAll(
                properties.keywords(), properties.location(), FetchRun.Trigger.SCHEDULED);

        log.info("Scheduled fetch complete: {} NEW jobs saved (fetched={}, duplicates={}) across {} source(s)",
                summary.totalSaved(), summary.totalFetched(), summary.totalDuplicates(), summary.sources().size());
        summary.sources().forEach(s -> {
            if (s.error() != null) {
                log.warn("  - {} FAILED: {}", s.source(), s.error());
            } else {
                log.info("  - {}: fetched={} saved={} duplicates={}",
                        s.source(), s.fetched(), s.saved(), s.duplicates());
            }
        });

        // Fetching and then not telling anyone is the shape of the problem this
        // phase exists to fix.
        notifier.sendQuietly("scheduled fetch");
    }
}
