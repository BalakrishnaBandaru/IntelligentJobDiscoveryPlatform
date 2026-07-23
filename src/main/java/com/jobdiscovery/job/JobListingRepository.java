package com.jobdiscovery.job;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link JobListing}.
 */
public interface JobListingRepository extends JpaRepository<JobListing, Long> {

    /**
     * Of the given content hashes, returns those that already exist in the table
     * — lets an ingest skip storing duplicates in a single round-trip.
     */
    @Query("select j.contentHash from JobListing j where j.contentHash in :hashes")
    Set<String> findExistingHashes(@Param("hashes") Collection<String> hashes);

    /** All listings from one source, newest first (by insertion order). */
    List<JobListing> findBySourceOrderByIdDesc(String source);
}
