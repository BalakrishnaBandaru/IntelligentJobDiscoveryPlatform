package com.jobdiscovery.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The tracker's only real logic is when {@code appliedAt} gets stamped, and it
 * is the kind of thing that quietly goes wrong: a date that silently rewrites
 * itself as the status advances would make "how long has this been sitting with
 * them?" unanswerable, which is most of what a tracker is for.
 */
class JobApplicationTest {

    private static final Instant DAY_ONE = Instant.parse("2026-08-01T09:00:00Z");
    private static final Instant DAY_TEN = DAY_ONE.plus(9, ChronoUnit.DAYS);

    @Test
    @DisplayName("saving a job for later does not count as having applied")
    void savedHasNoAppliedDate() {
        JobApplication saved = new JobApplication(1L, ApplicationStatus.SAVED, null, DAY_ONE);

        assertNull(saved.getAppliedAt());
        assertFalse(ApplicationStatus.SAVED.isSubmitted());
    }

    @Test
    @DisplayName("tracking a job as already applied stamps the date immediately")
    void appliedStampsTheDate() {
        JobApplication applied = new JobApplication(1L, ApplicationStatus.APPLIED, null, DAY_ONE);

        assertEquals(DAY_ONE, applied.getAppliedAt());
    }

    @Test
    @DisplayName("moving from saved to applied stamps the date then")
    void savedToAppliedStampsOnTransition() {
        JobApplication application =
                new JobApplication(1L, ApplicationStatus.SAVED, null, DAY_ONE);

        application.updateStatus(ApplicationStatus.APPLIED, DAY_TEN);

        assertEquals(DAY_TEN, application.getAppliedAt(),
                "the date you applied is when you applied, not when you saved it");
    }

    @Test
    @DisplayName("advancing through the funnel never rewrites the applied date")
    void appliedDateIsNotOverwritten() {
        // The bug this guards against: stamping on every submitted status would
        // make an application look freshly submitted each time it progressed,
        // and "how long have they had this?" is the question a tracker exists
        // to answer.
        JobApplication application =
                new JobApplication(1L, ApplicationStatus.APPLIED, null, DAY_ONE);

        application.updateStatus(ApplicationStatus.SCREENING, DAY_TEN);
        application.updateStatus(ApplicationStatus.INTERVIEW, DAY_TEN.plus(5, ChronoUnit.DAYS));

        assertEquals(DAY_ONE, application.getAppliedAt());
        assertEquals(ApplicationStatus.INTERVIEW, application.getStatus());
    }

    @Test
    @DisplayName("a rejection still counts as having applied")
    void rejectionImpliesApplication() {
        // Someone tracking a job only once it is rejected should still get a
        // date, otherwise the funnel counts a rejection with no application.
        JobApplication application =
                new JobApplication(1L, ApplicationStatus.REJECTED, "no response", DAY_ONE);

        assertNotNull(application.getAppliedAt());
        assertTrue(ApplicationStatus.REJECTED.isSubmitted());
        assertTrue(ApplicationStatus.REJECTED.isTerminal());
    }

    @Test
    @DisplayName("updating notes touches updatedAt but not the status or applied date")
    void notesUpdateIsNarrow() {
        JobApplication application =
                new JobApplication(1L, ApplicationStatus.APPLIED, "first note", DAY_ONE);

        application.updateNotes("recruiter called", DAY_TEN);

        assertEquals("recruiter called", application.getNotes());
        assertEquals(ApplicationStatus.APPLIED, application.getStatus());
        assertEquals(DAY_ONE, application.getAppliedAt());
        assertEquals(DAY_TEN, application.getUpdatedAt());
        assertEquals(DAY_ONE, application.getCreatedAt(), "createdAt must not move");
    }

    @Test
    @DisplayName("only SAVED is a not-yet-applied state")
    void onlySavedIsUnsubmitted() {
        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertEquals(status != ApplicationStatus.SAVED, status.isSubmitted(),
                    status + " reported the wrong submitted state");
        }
    }

    @Test
    @DisplayName("the statuses are declared in funnel order")
    void funnelOrderIsMeaningful() {
        // Ordinal is used for display order, so the declaration order is load-
        // bearing rather than cosmetic.
        assertTrue(ApplicationStatus.SAVED.ordinal() < ApplicationStatus.APPLIED.ordinal());
        assertTrue(ApplicationStatus.APPLIED.ordinal() < ApplicationStatus.SCREENING.ordinal());
        assertTrue(ApplicationStatus.SCREENING.ordinal() < ApplicationStatus.INTERVIEW.ordinal());
        assertTrue(ApplicationStatus.INTERVIEW.ordinal() < ApplicationStatus.OFFER.ordinal());
        assertTrue(ApplicationStatus.OFFER.ordinal() < ApplicationStatus.REJECTED.ordinal(),
                "terminal states belong at the end, not mid-funnel");
    }
}
