-- Phase 6 groundwork: a record of every fetch run.
--
-- Added 2026-08-21 after discovering the pipeline had not fetched anything for
-- 25 days without that being visible anywhere. The daily cron fires at 06:00
-- IST, but the container only exists while Docker Desktop is running, so in
-- practice it almost never fired -- and nothing recorded that it had not. The
-- job_listing table could not answer the question either: de-duplication means
-- a run that finds only duplicates writes no rows at all, so "no new jobs" and
-- "no run happened" looked identical.
--
-- This table separates those two states. It is also what makes the startup
-- fetch's guard correct: without a record of *attempts*, a run that legitimately
-- found nothing new would look stale and be repeated on every restart, burning
-- API quota.

create table fetch_run (
    id                bigint generated always as identity primary key,
    -- When the run started. The guard compares this against now().
    ran_at            timestamptz  not null,
    -- STARTUP | SCHEDULED | MANUAL -- which path triggered it. Kept as text
    -- rather than an enum type so adding a trigger needs no migration.
    trigger_source    varchar(20)  not null,
    keywords          varchar(255),
    location          varchar(255),
    total_fetched     integer      not null default 0,
    total_saved       integer      not null default 0,
    total_duplicates  integer      not null default 0,
    -- Per-source breakdown, human-readable. Not queried, only displayed.
    sources_summary   text,
    -- Set when the whole run failed. Individual source failures are recorded in
    -- sources_summary instead, since one source failing does not abort the rest.
    error             text
);

-- The guard reads only the most recent run, on every startup.
create index ix_fetch_run_ran_at on fetch_run (ran_at desc);
