-- Phase 1: the job_listing table. Flyway owns the schema from here on
-- (spring.jpa.hibernate.ddl-auto=none), so every schema change is an
-- explicit, versioned migration.

create table job_listing (
    id           bigint generated always as identity primary key,
    title        varchar(512)  not null,
    company      varchar(255),
    location     varchar(512),
    description  text,
    apply_url    varchar(1024) not null,
    source       varchar(32)   not null,
    posted_date  timestamptz,
    fetched_at   timestamptz   not null
);
