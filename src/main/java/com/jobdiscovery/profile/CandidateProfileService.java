package com.jobdiscovery.profile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes the single candidate profile. As a single-user tool there is
 * at most one profile, so {@link #save} upserts: it updates the existing profile
 * if present, otherwise creates one.
 */
@Service
public class CandidateProfileService {

    private final CandidateProfileRepository repository;

    public CandidateProfileService(CandidateProfileRepository repository) {
        this.repository = repository;
    }

    /** Creates the profile, or replaces the existing one, and returns it. */
    @Transactional
    public CandidateProfile save(CandidateProfileRequest request) {
        CandidateProfile profile = repository.findFirstByOrderByIdAsc()
                .orElseGet(CandidateProfile::new);

        Instant now = Instant.now();
        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(now);
        }
        profile.setUpdatedAt(now);

        profile.setSkills(clean(request.skills()));
        profile.setExperienceYears(request.experienceYears());
        profile.setPreferredLocations(clean(request.preferredLocations()));
        profile.setPreferredCompanies(clean(request.preferredCompanies()));
        profile.setExpectedSalary(request.expectedSalary());
        profile.setNoticePeriodDays(request.noticePeriodDays());
        profile.setKeywords(clean(request.keywords()));

        return repository.save(profile);
    }

    /** The current profile, or throws {@link ProfileNotFoundException} if unset. */
    @Transactional(readOnly = true)
    public CandidateProfile get() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(ProfileNotFoundException::new);
    }

    /** Removes the profile if one exists (handy for re-seeding). */
    @Transactional
    public void delete() {
        repository.deleteAll();
    }

    /** Trim each value, drop blanks and duplicates, preserve order; null → empty. */
    private List<String> clean(List<String> values) {
        List<String> cleaned = new ArrayList<>();
        if (values == null) {
            return cleaned;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty() && !cleaned.contains(trimmed)) {
                cleaned.add(trimmed);
            }
        }
        return cleaned;
    }
}
