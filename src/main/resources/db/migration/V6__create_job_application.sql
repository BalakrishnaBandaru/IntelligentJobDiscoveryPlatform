-- Phase 7: track which listings have been applied to, and what happened next.
--
-- Separate from job_listing.notified_at, which answers a different question.
-- "Notified" means the digest told you about it; "application" means you acted.
-- Conflating them would mean either re-notifying jobs you have already applied
-- to, or treating a glance at the digest as an application.
--
-- One application per listing: this is a personal tracker, and applying twice to
-- the same posting is a mistake rather than a case to model. The unique
-- constraint makes that explicit instead of leaving duplicates to be noticed
-- later.

create table job_application (
    id              bigint generated always as identity primary key,
    job_listing_id  bigint       not null references job_listing (id) on delete cascade,
    -- SAVED | APPLIED | SCREENING | INTERVIEW | OFFER | REJECTED | WITHDRAWN.
    -- Text rather than a Postgres enum so adding a stage needs no migration.
    status          varchar(20)  not null,
    -- When the application itself was submitted. Null while status is SAVED,
    -- which is the "shortlisted, not yet applied" state.
    applied_at      timestamptz,
    -- Free-text: recruiter name, referral, salary discussed, why rejected.
    notes           text,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,

    -- Applying twice to one posting is an error, not a scenario.
    constraint ux_job_application_listing unique (job_listing_id)
);

-- The two reads that matter: the funnel view (group by status) and the
-- "exclude what I have already acted on" filter used by the digest.
create index ix_job_application_status on job_application (status);
