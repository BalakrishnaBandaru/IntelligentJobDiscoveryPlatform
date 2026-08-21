package com.jobdiscovery.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating an application.
 *
 * @param jobId  the stored listing being applied to
 * @param status defaults to {@code APPLIED} when omitted — tracking a job is
 *               nearly always something you do at the moment you apply. Pass
 *               {@code SAVED} explicitly for the shortlist-for-later case
 * @param notes  optional; recruiter, referral, salary discussed
 */
public record ApplicationRequest(
        @NotNull(message = "jobId is required") Long jobId,
        ApplicationStatus status,
        @Size(max = 10_000, message = "notes must be 10000 characters or fewer") String notes) {

    public ApplicationStatus statusOrDefault() {
        return status == null ? ApplicationStatus.APPLIED : status;
    }
}
