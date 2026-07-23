-- V2: content-based de-duplication.
--
-- content_hash = SHA-256 of the normalised (title | company | location).
-- A unique index on it prevents duplicate postings from being stored, including
-- the same role re-fetched later or arriving from a second source.
--
-- Legacy Phase-1 rows predate this column and carry no hash; they are
-- disposable pre-dedup fetch data, so we clear them, which lets the new column
-- be NOT NULL. On a fresh database this DELETE affects nothing.
delete from job_listing;

alter table job_listing add column content_hash varchar(64) not null;
create unique index ux_job_listing_content_hash on job_listing (content_hash);
