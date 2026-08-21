package com.jobdiscovery.application;

import jakarta.validation.constraints.Size;

/**
 * Payload for updating an application. Both fields are optional so a caller can
 * change the status without resending notes, or annotate without moving the
 * status — sending only one must not blank the other.
 */
public record ApplicationUpdateRequest(
        ApplicationStatus status,
        @Size(max = 10_000, message = "notes must be 10000 characters or fewer") String notes) {
}
