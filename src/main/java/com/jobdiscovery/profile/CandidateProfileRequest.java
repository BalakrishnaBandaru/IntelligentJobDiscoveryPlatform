package com.jobdiscovery.profile;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

/**
 * Incoming payload for {@code POST /api/profile}. Only {@code skills} and
 * {@code experienceYears} are required — they are the minimum the Phase 5 rule
 * engine needs to score a job. The list fields are cleaned (trimmed, blanks and
 * duplicates dropped) in {@link CandidateProfileService} before persisting.
 *
 * @param skills             technical skills (at least one required)
 * @param experienceYears    total years of experience (required, &ge; 0)
 * @param preferredLocations desired work locations (optional)
 * @param preferredCompanies desired employers (optional)
 * @param expectedSalary     expected annual CTC in INR (optional, &ge; 0)
 * @param noticePeriodDays   notice period in days (optional, &ge; 0)
 * @param keywords           extra search keywords beyond skills (optional)
 */
public record CandidateProfileRequest(
        @NotEmpty(message = "at least one skill is required") List<String> skills,
        @NotNull(message = "experienceYears is required")
        @PositiveOrZero(message = "experienceYears must be zero or greater") Integer experienceYears,
        List<String> preferredLocations,
        List<String> preferredCompanies,
        @PositiveOrZero(message = "expectedSalary must be zero or greater") BigDecimal expectedSalary,
        @PositiveOrZero(message = "noticePeriodDays must be zero or greater") Integer noticePeriodDays,
        List<String> keywords) {
}
