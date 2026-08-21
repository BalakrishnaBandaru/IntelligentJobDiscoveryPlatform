package com.jobdiscovery.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The startup fetch's guard is what stands between a container restart and a
 * live API call, so it is worth testing properly: during development this
 * container was recreated a dozen times in an afternoon, and an unguarded
 * version would have spent a dozen fetches of real quota.
 */
class StartupFetchJobTest {

    /** A fetch service that records calls instead of making them. */
    private static final class RecordingFetchService extends JobFetchService {

        private final List<FetchRun.Trigger> calls = new ArrayList<>();
        private Duration sinceLastRun;
        private RuntimeException failWith;

        RecordingFetchService(Duration sinceLastRun) {
            super(List.of(), null, null);
            this.sinceLastRun = sinceLastRun;
        }

        @Override
        public Optional<Duration> timeSinceLastRun(Instant now) {
            return Optional.ofNullable(sinceLastRun);
        }

        @Override
        public FetchSummary fetchAll(String keywords, String location, FetchRun.Trigger trigger) {
            calls.add(trigger);
            if (failWith != null) {
                throw failWith;
            }
            return new FetchSummary(keywords, location, List.of(), 0, 0, 0);
        }
    }

    private StartupFetchJob job(RecordingFetchService service, int maxAgeHours) {
        return new StartupFetchJob(service,
                new FetchStartupProperties(true, maxAgeHours),
                new FetchScheduleProperties(true, "0 0 6 * * *", "Asia/Kolkata",
                        "java developer", "bangalore"));
    }

    @Test
    @DisplayName("fetches when the last attempt is older than the threshold")
    void fetchesWhenStale() {
        RecordingFetchService service = new RecordingFetchService(Duration.ofHours(25));
        job(service, 12).run();

        assertEquals(List.of(FetchRun.Trigger.STARTUP), service.calls);
    }

    @Test
    @DisplayName("skips when a fetch already ran inside the threshold")
    void skipsWhenFresh() {
        // The restart-storm case: without this, every container recreate is a
        // live call to two job boards.
        RecordingFetchService service = new RecordingFetchService(Duration.ofHours(2));
        job(service, 12).run();

        assertTrue(service.calls.isEmpty(), "a recent run must suppress the startup fetch");
    }

    @Test
    @DisplayName("fetches on a completely empty database")
    void fetchesWhenNeverRun() {
        // No run has ever been recorded — a first boot, or the state this
        // feature was written for, where runs were never recorded at all.
        RecordingFetchService service = new RecordingFetchService(null);
        job(service, 12).run();

        assertEquals(List.of(FetchRun.Trigger.STARTUP), service.calls);
    }

    @Test
    @DisplayName("exactly at the threshold counts as stale")
    void thresholdIsInclusive() {
        RecordingFetchService service = new RecordingFetchService(Duration.ofHours(12));
        job(service, 12).run();

        assertEquals(1, service.calls.size(), "12h with a 12h threshold should fetch");
    }

    @Test
    @DisplayName("a failing fetch is swallowed, because the app is already serving")
    void failureDoesNotPropagate() {
        // The stored listings are still rankable without a successful fetch, so
        // an unreachable job board must not surface as a startup error.
        RecordingFetchService service = new RecordingFetchService(Duration.ofHours(25));
        service.failWith = new IllegalStateException("Adzuna unreachable");

        job(service, 12).run();

        assertEquals(1, service.calls.size(), "it should have tried");
    }
}
