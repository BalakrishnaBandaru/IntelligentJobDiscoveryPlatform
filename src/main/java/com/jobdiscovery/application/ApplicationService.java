package com.jobdiscovery.application;

import com.jobdiscovery.job.JobListing;
import com.jobdiscovery.job.JobListingRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The application tracker.
 *
 * <p>Its value is not the CRUD — it is that acting on a job feeds back into the
 * rest of the pipeline. Once a listing is tracked, the digest stops announcing
 * it and the ranked list shows where it got to, so the shortlist stays a list of
 * things still to decide rather than a list you have to mentally filter.
 */
@Service
public class ApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final JobListingRepository jobRepository;

    public ApplicationService(JobApplicationRepository applicationRepository,
                              JobListingRepository jobRepository) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
    }

    /**
     * Starts tracking a listing.
     *
     * @throws ApplicationException if the listing does not exist, or is already
     *                              tracked — applying twice to one posting is a
     *                              mistake rather than a case to support
     */
    @Transactional
    public ApplicationView create(ApplicationRequest request) {
        JobListing listing = jobRepository.findById(request.jobId())
                .orElseThrow(() -> new ApplicationException(ApplicationException.NOT_FOUND,
                        "No job listing with id " + request.jobId() + "."));

        applicationRepository.findByJobListingId(request.jobId()).ifPresent(existing -> {
            throw new ApplicationException(ApplicationException.ALREADY_TRACKED,
                    "Job " + request.jobId() + " is already tracked as application "
                    + existing.getId() + " (" + existing.getStatus() + "). Update that instead.");
        });

        JobApplication application = new JobApplication(
                request.jobId(), request.statusOrDefault(), request.notes(), Instant.now());
        return toView(applicationRepository.save(application), listing);
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> list(ApplicationStatus status) {
        List<JobApplication> applications = status == null
                ? applicationRepository.findAllByOrderByUpdatedAtDesc()
                : applicationRepository.findByStatusOrderByUpdatedAtDesc(status);
        return withListings(applications);
    }

    @Transactional(readOnly = true)
    public ApplicationView get(Long id) {
        JobApplication application = require(id);
        return toView(application, jobRepository.findById(application.getJobListingId()).orElse(null));
    }

    /** Applies whichever fields the caller actually sent. */
    @Transactional
    public ApplicationView update(Long id, ApplicationUpdateRequest request) {
        JobApplication application = require(id);
        Instant now = Instant.now();
        if (request.status() != null) {
            application.updateStatus(request.status(), now);
        }
        if (request.notes() != null) {
            application.updateNotes(request.notes(), now);
        }
        return toView(applicationRepository.save(application),
                jobRepository.findById(application.getJobListingId()).orElse(null));
    }

    @Transactional
    public void delete(Long id) {
        applicationRepository.delete(require(id));
    }

    /**
     * Counts per status, every status present even at zero.
     *
     * <p>A funnel with gaps in it is harder to read than one with zeroes, and
     * "no rejections yet" is information rather than an absence.
     */
    @Transactional(readOnly = true)
    public Map<ApplicationStatus, Long> funnel() {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : applicationRepository.countByStatus()) {
            counts.put((ApplicationStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** Listing ids that are tracked at all — used to filter the digest. */
    @Transactional(readOnly = true)
    public Set<Long> trackedJobIds() {
        return applicationRepository.findTrackedJobListingIds();
    }

    /** Status per listing id, for annotating the ranked shortlist. */
    @Transactional(readOnly = true)
    public Map<Long, ApplicationStatus> statusByJobId() {
        Map<Long, ApplicationStatus> byJob = new HashMap<>();
        for (JobApplication application : applicationRepository.findAll()) {
            byJob.put(application.getJobListingId(), application.getStatus());
        }
        return byJob;
    }

    private JobApplication require(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ApplicationException.NOT_FOUND,
                        "No application with id " + id + "."));
    }

    /** Bulk-loads the listings so a list of N applications is two queries, not N+1. */
    private List<ApplicationView> withListings(List<JobApplication> applications) {
        Set<Long> jobIds = new HashSet<>();
        for (JobApplication application : applications) {
            jobIds.add(application.getJobListingId());
        }
        Map<Long, JobListing> listings = new HashMap<>();
        for (JobListing listing : jobRepository.findAllById(jobIds)) {
            listings.put(listing.getId(), listing);
        }

        List<ApplicationView> views = new ArrayList<>(applications.size());
        for (JobApplication application : applications) {
            views.add(toView(application, listings.get(application.getJobListingId())));
        }
        return views;
    }

    /**
     * @param listing may be null if the listing was deleted out from under the
     *                application; the tracker should still show what it knows
     *                rather than failing the whole request
     */
    private ApplicationView toView(JobApplication application, JobListing listing) {
        return new ApplicationView(
                application.getId(),
                application.getJobListingId(),
                listing == null ? "(listing deleted)" : listing.getTitle(),
                listing == null ? null : listing.getCompany(),
                listing == null ? null : listing.getLocation(),
                listing == null ? null : listing.getApplyUrl(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getNotes(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
