package com.jobdiscovery.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA repository for {@link JobApplication}. */
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    /** All applications, most recently touched first. */
    List<JobApplication> findAllByOrderByUpdatedAtDesc();

    List<JobApplication> findByStatusOrderByUpdatedAtDesc(ApplicationStatus status);

    /** Enforces the one-application-per-listing rule before insert. */
    Optional<JobApplication> findByJobListingId(Long jobListingId);

    /**
     * Listing ids that already have an application, so the digest can stop
     * announcing jobs you have already acted on.
     */
    @Query("select a.jobListingId from JobApplication a")
    Set<Long> findTrackedJobListingIds();

    /** Counts per status, for the funnel view. */
    @Query("select a.status, count(a) from JobApplication a group by a.status")
    List<Object[]> countByStatus();
}
