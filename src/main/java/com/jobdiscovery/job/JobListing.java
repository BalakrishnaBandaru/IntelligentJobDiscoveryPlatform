package com.jobdiscovery.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A single job posting, normalised into our own shape from whichever source API
 * it came from (Adzuna in Phase 1).
 *
 * The table is created and owned by the Flyway migration
 * {@code V1__create_job_listing_table.sql}; Hibernate does not manage the schema
 * ({@code spring.jpa.hibernate.ddl-auto=none}).
 */
@Entity
@Table(name = "job_listing")
public class JobListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(length = 255)
    private String company;

    @Column(length = 512)
    private String location;

    @Column(columnDefinition = "text")
    private String description;

    /** The link a human clicks to view/apply to the posting on the source site. */
    @Column(name = "apply_url", nullable = false, length = 1024)
    private String applyUrl;

    /** Which source API this listing came from, e.g. "ADZUNA". */
    @Column(nullable = false, length = 32)
    private String source;

    /** When the posting was published, as reported by the source (may be null). */
    @Column(name = "posted_date")
    private Instant postedDate;

    /** When we fetched and stored this row. */
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected JobListing() {
        // Required by JPA.
    }

    /** Creates a new listing to persist; {@code id} is assigned by the database. */
    public JobListing(String title, String company, String location, String description,
                      String applyUrl, String source, Instant postedDate, Instant fetchedAt) {
        this.title = title;
        this.company = company;
        this.location = location;
        this.description = description;
        this.applyUrl = applyUrl;
        this.source = source;
        this.postedDate = postedDate;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getApplyUrl() {
        return applyUrl;
    }

    public void setApplyUrl(String applyUrl) {
        this.applyUrl = applyUrl;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getPostedDate() {
        return postedDate;
    }

    public void setPostedDate(Instant postedDate) {
        this.postedDate = postedDate;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
