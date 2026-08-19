-- Phase 4: the candidate profile that drives Phase 5 scoring.
--
-- This is a single-user personal tool, so in practice there is exactly one
-- profile row and the app treats it as a singleton (POST /api/profile upserts
-- it). The table still carries a surrogate id, so the model extends cleanly to
-- multiple named profiles later without a schema change.
--
-- The list-valued fields (skills, keywords, preferred locations/companies) live
-- in standard JPA @ElementCollection child tables, each keyed by profile_id, so
-- they stay queryable and portable rather than being crammed into one column.

create table candidate_profile (
    id                  bigint generated always as identity primary key,
    experience_years    integer        not null,
    expected_salary     numeric(12, 2),
    notice_period_days  integer,
    created_at          timestamptz    not null,
    updated_at          timestamptz    not null
);

create table candidate_profile_skills (
    profile_id  bigint       not null references candidate_profile (id) on delete cascade,
    skill       varchar(128) not null
);
create index ix_candidate_profile_skills on candidate_profile_skills (profile_id);

create table candidate_profile_keywords (
    profile_id  bigint       not null references candidate_profile (id) on delete cascade,
    keyword     varchar(128) not null
);
create index ix_candidate_profile_keywords on candidate_profile_keywords (profile_id);

create table candidate_profile_preferred_locations (
    profile_id  bigint       not null references candidate_profile (id) on delete cascade,
    location    varchar(255) not null
);
create index ix_candidate_profile_preferred_locations on candidate_profile_preferred_locations (profile_id);

create table candidate_profile_preferred_companies (
    profile_id  bigint       not null references candidate_profile (id) on delete cascade,
    company     varchar(255) not null
);
create index ix_candidate_profile_preferred_companies on candidate_profile_preferred_companies (profile_id);
