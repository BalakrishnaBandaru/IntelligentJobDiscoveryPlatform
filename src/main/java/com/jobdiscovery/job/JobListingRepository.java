package com.jobdiscovery.job;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link JobListing}. Gives us CRUD, paging and
 * derived queries for free; custom finders (e.g. de-duplication lookups) are
 * added in later phases.
 */
public interface JobListingRepository extends JpaRepository<JobListing, Long> {
}
