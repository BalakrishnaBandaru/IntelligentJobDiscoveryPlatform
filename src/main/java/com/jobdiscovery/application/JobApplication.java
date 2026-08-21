package com.jobdiscovery.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One application against one stored listing.
 *
 * <p>Distinct from {@code job_listing.notified_at}, which records that the
 * digest mentioned a job. This records that you did something about it.
 *
 * <p>The table is owned by {@code V6__create_job_application.sql}; Hibernate
 * does not manage the schema.
 */
@Entity
@Table(name = "job_application")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The listing applied to. Stored as a plain id rather than a
     * {@code @ManyToOne} so reading an application never drags a whole
     * JobListing (description included) along with it — the tracker's list view
     * wants the id and little else.
     */
    @Column(name = "job_listing_id", nullable = false)
    private Long jobListingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    /** Null while the status is {@code SAVED}. */
    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobApplication() {
        // JPA.
    }

    public JobApplication(Long jobListingId, ApplicationStatus status, String notes, Instant now) {
        this.jobListingId = jobListingId;
        this.status = status;
        this.notes = notes;
        this.createdAt = now;
        this.updatedAt = now;
        // Creating something already past SAVED means it was applied to now —
        // most often when a job is tracked only after the fact.
        this.appliedAt = status.isSubmitted() ? now : null;
    }

    /**
     * Moves the application on.
     *
     * <p>{@code appliedAt} is stamped the first time the status implies a
     * submission and never overwritten after — moving APPLIED → INTERVIEW must
     * not rewrite the date you applied.
     */
    public void updateStatus(ApplicationStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
        if (newStatus.isSubmitted() && this.appliedAt == null) {
            this.appliedAt = now;
        }
    }

    public void updateNotes(String notes, Instant now) {
        this.notes = notes;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getJobListingId() {
        return jobListingId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
