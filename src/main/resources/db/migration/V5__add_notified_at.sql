-- Phase 6: remember which listings have already been sent in a digest.
--
-- Without this the digest is the same every day. The shortlist is recomputed
-- from the whole table on each run, so the top ten barely changes between one
-- morning and the next -- a notification that repeats itself gets ignored, and
-- an ignored notification is worse than none.
--
-- Deliberately a timestamp rather than a boolean: knowing *when* something was
-- sent means a digest can be re-sent for a window, and it dates the decision if
-- the threshold is later changed.
--
-- Null means never sent, which is the correct state for the 86 rows that
-- predate this column -- the first digest should be free to include them.

alter table job_listing add column notified_at timestamptz;

-- The digest query is "high-scoring AND not yet notified"; the scoring half is
-- in-memory, so this index only has to make the second half cheap.
create index ix_job_listing_notified_at on job_listing (notified_at);
