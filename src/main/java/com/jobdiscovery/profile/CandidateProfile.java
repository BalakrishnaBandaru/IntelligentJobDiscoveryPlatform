package com.jobdiscovery.profile;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The candidate's profile — the target we score jobs against in Phase 5. As a
 * single-user tool there is one profile ("the candidate"); it is created and
 * replaced through {@code POST /api/profile}.
 *
 * <p>The table and its child collection tables are owned by the Flyway migration
 * {@code V3__create_candidate_profile.sql}; Hibernate does not manage the schema
 * ({@code spring.jpa.hibernate.ddl-auto=none}). The list fields are fetched
 * eagerly so the entity serialises cleanly with {@code open-in-view=false}.
 */
@Entity
@Table(name = "candidate_profile")
public class CandidateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Technical skills, e.g. "Java", "Spring Boot", "PostgreSQL". */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_profile_skills",
            joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "skill", nullable = false, length = 128)
    private List<String> skills = new ArrayList<>();

    /** Total years of professional experience. */
    @Column(name = "experience_years", nullable = false)
    private Integer experienceYears;

    /** Locations the candidate wants to work in, e.g. "Bangalore", "Remote". */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_profile_preferred_locations",
            joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "location", nullable = false, length = 255)
    private List<String> preferredLocations = new ArrayList<>();

    /** Companies the candidate would especially like to work for (optional). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_profile_preferred_companies",
            joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "company", nullable = false, length = 255)
    private List<String> preferredCompanies = new ArrayList<>();

    /** Expected annual CTC (in INR); optional. */
    @Column(name = "expected_salary", precision = 12, scale = 2)
    private BigDecimal expectedSalary;

    /** Notice period in days; optional. */
    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    /** Free-form search keywords beyond skills, e.g. "microservices", "backend". */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_profile_keywords",
            joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "keyword", nullable = false, length = 128)
    private List<String> keywords = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CandidateProfile() {
        // Required by JPA.
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public Integer getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(Integer experienceYears) {
        this.experienceYears = experienceYears;
    }

    public List<String> getPreferredLocations() {
        return preferredLocations;
    }

    public void setPreferredLocations(List<String> preferredLocations) {
        this.preferredLocations = preferredLocations;
    }

    public List<String> getPreferredCompanies() {
        return preferredCompanies;
    }

    public void setPreferredCompanies(List<String> preferredCompanies) {
        this.preferredCompanies = preferredCompanies;
    }

    public BigDecimal getExpectedSalary() {
        return expectedSalary;
    }

    public void setExpectedSalary(BigDecimal expectedSalary) {
        this.expectedSalary = expectedSalary;
    }

    public Integer getNoticePeriodDays() {
        return noticePeriodDays;
    }

    public void setNoticePeriodDays(Integer noticePeriodDays) {
        this.noticePeriodDays = noticePeriodDays;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
