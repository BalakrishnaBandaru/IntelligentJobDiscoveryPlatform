package com.jobdiscovery.fetch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A record that a fetch was attempted, and what came of it.
 *
 * <p>Exists because "no new jobs" and "no run happened" were previously
 * indistinguishable: de-duplication means a run that finds only duplicates
 * writes nothing to {@code job_listing}, so a pipeline that had not run for 25
 * days looked exactly like one that was running and finding nothing. Recording
 * the <i>attempt</i> separates the two.
 *
 * <p>The table is owned by {@code V4__create_fetch_run.sql}; Hibernate does not
 * manage the schema.
 */
@Entity
@Table(name = "fetch_run")
public class FetchRun {

    /** Which path triggered a run. */
    public enum Trigger { STARTUP, SCHEDULED, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ran_at", nullable = false)
    private Instant ranAt;

    @Column(name = "trigger_source", nullable = false, length = 20)
    private String triggerSource;

    @Column(length = 255)
    private String keywords;

    @Column(length = 255)
    private String location;

    @Column(name = "total_fetched", nullable = false)
    private int totalFetched;

    @Column(name = "total_saved", nullable = false)
    private int totalSaved;

    @Column(name = "total_duplicates", nullable = false)
    private int totalDuplicates;

    @Column(name = "sources_summary", columnDefinition = "text")
    private String sourcesSummary;

    @Column(columnDefinition = "text")
    private String error;

    protected FetchRun() {
        // JPA.
    }

    public FetchRun(Instant ranAt, Trigger trigger, String keywords, String location) {
        this.ranAt = ranAt;
        this.triggerSource = trigger.name();
        this.keywords = keywords;
        this.location = location;
    }

    /** Fills in the outcome once the run has finished. */
    public void recordSuccess(FetchSummary summary) {
        this.totalFetched = summary.totalFetched();
        this.totalSaved = summary.totalSaved();
        this.totalDuplicates = summary.totalDuplicates();
        StringBuilder sb = new StringBuilder();
        for (SourceOutcome outcome : summary.sources()) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(outcome.source()).append(outcome.error() != null
                    ? "=FAILED(" + outcome.error() + ")"
                    : "=fetched " + outcome.fetched() + "/saved " + outcome.saved());
        }
        this.sourcesSummary = sb.toString();
    }

    /** Records that the run itself blew up, as opposed to one source failing. */
    public void recordFailure(String message) {
        this.error = message;
    }

    public Long getId() {
        return id;
    }

    public Instant getRanAt() {
        return ranAt;
    }

    public String getTriggerSource() {
        return triggerSource;
    }

    public String getKeywords() {
        return keywords;
    }

    public String getLocation() {
        return location;
    }

    public int getTotalFetched() {
        return totalFetched;
    }

    public int getTotalSaved() {
        return totalSaved;
    }

    public int getTotalDuplicates() {
        return totalDuplicates;
    }

    public String getSourcesSummary() {
        return sourcesSummary;
    }

    public String getError() {
        return error;
    }
}
