package com.jobdiscovery.fetch;

import com.jobdiscovery.notify.DigestNotifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Fetches once at startup, if the data is stale.
 *
 * <p><b>Why this exists.</b> The daily cron is the right idea and the wrong
 * deployment model. It fires at 06:00 IST, but the container only exists while
 * Docker Desktop is running on a laptop, so it almost never fires — on
 * 2026-08-21 the newest stored posting was 25 days old and nothing had recorded
 * that the pipeline had stopped. A schedule assumes an always-on host; this
 * assumes the opposite, which is what is actually true here. The two are
 * complementary: keep the cron for the case where the stack does stay up.
 *
 * <p>Guarded on the age of the last <i>attempt</i> rather than the newest job,
 * because de-duplication means a run that legitimately finds nothing new writes
 * no rows — using job age would re-fetch on every restart and burn API quota.
 * During development that would have meant a live fetch on each of a dozen
 * container recreates.
 *
 * <p>Runs on its own thread. {@link ApplicationReadyEvent} listeners run on the
 * startup thread, and a fetch hits two external APIs — blocking there would
 * delay the health check and could fail the container's start period.
 *
 * <p>Disable with {@code fetch.startup.enabled=false}, which is what the test
 * configuration does: a fetch during a {@code @SpringBootTest} would call the
 * live job boards and spend real quota.
 */
@Component
@ConditionalOnProperty(name = "fetch.startup.enabled", havingValue = "true", matchIfMissing = true)
public class StartupFetchJob {

    private static final Logger log = LoggerFactory.getLogger(StartupFetchJob.class);

    private final JobFetchService fetchService;
    private final FetchStartupProperties startupProperties;
    private final FetchScheduleProperties scheduleProperties;
    private final DigestNotifier notifier;

    public StartupFetchJob(JobFetchService fetchService,
                           FetchStartupProperties startupProperties,
                           FetchScheduleProperties scheduleProperties,
                           DigestNotifier notifier) {
        this.fetchService = fetchService;
        this.startupProperties = startupProperties;
        this.scheduleProperties = scheduleProperties;
        this.notifier = notifier;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void fetchIfStale() {
        Thread worker = new Thread(this::run, "startup-fetch");
        worker.setDaemon(true);
        worker.start();
    }

    /** Package-private so a test can drive it without an application context. */
    void run() {
        Duration maxAge = Duration.ofHours(startupProperties.maxAgeHours());
        Optional<Duration> sinceLastRun = fetchService.timeSinceLastRun(Instant.now());

        if (sinceLastRun.isPresent() && sinceLastRun.get().compareTo(maxAge) < 0) {
            log.info("Startup fetch skipped: last run was {}h ago, under the {}h threshold",
                    sinceLastRun.get().toHours(), maxAge.toHours());
            // Still worth a digest: the fetch was skipped because it ran
            // recently, not because there is nothing new to report.
            notifier.sendOnStartup("startup (fetch skipped)");
            return;
        }

        String age = sinceLastRun
                .map(d -> d.toHours() + "h ago")
                .orElse("never");
        log.info("Startup fetch starting (last run: {}; keywords='{}', location='{}')...",
                age, scheduleProperties.keywords(), scheduleProperties.location());

        try {
            FetchSummary summary = fetchService.fetchAll(
                    scheduleProperties.keywords(), scheduleProperties.location(),
                    FetchRun.Trigger.STARTUP);

            log.info("Startup fetch complete: {} NEW jobs saved (fetched={}, duplicates={})",
                    summary.totalSaved(), summary.totalFetched(), summary.totalDuplicates());
            summary.sources().forEach(s -> {
                if (s.error() != null) {
                    log.warn("  - {} FAILED: {}", s.source(), s.error());
                } else {
                    log.info("  - {}: fetched={} saved={} duplicates={}",
                            s.source(), s.fetched(), s.saved(), s.duplicates());
                }
            });
            notifier.sendOnStartup("startup fetch");
        } catch (Exception e) {
            // The app is already serving. A failed fetch must not take it down —
            // the stored listings are still rankable.
            log.error("Startup fetch failed: {}", e.getMessage(), e);
        }
    }
}
