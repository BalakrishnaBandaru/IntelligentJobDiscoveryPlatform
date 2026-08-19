package com.jobdiscovery.profile;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link CandidateProfile}.
 */
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    /**
     * The single profile, if one has been set. This is a single-user tool, so we
     * treat the lowest-id row as "the candidate profile".
     */
    Optional<CandidateProfile> findFirstByOrderByIdAsc();
}
