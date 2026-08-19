# Project State — Intelligent Job Discovery Platform

> Persistent hand-off doc so a fresh session can resume instantly without
> re-explaining the project. **Claude reads this first at the start of every
> session** and **updates it at the end of every phase or significant change.**
>
> _Last updated: 2026-08-19._

---

## 📍 Current phase

**Phase 5a — Deterministic rule engine — BUILT (2026-08-19), NOT YET RUN.**
Written but not compiled or executed: Docker Desktop was down for the whole
session, so nothing here has been through a build. Next up: **verify 5a against
a running stack**, then **Phase 5b — LLM match explanations**.

---

## ✅ Phases completed

- [x] **Phase 0 — Setup / Docker scaffold** — **DONE (2026-07-22).**
  `docker compose up --build` brings up Postgres 18 + Adminer + app; health = UP
  with DB connected. Fully containerised, no host installs.
- [x] **Phase 1 — Fetch jobs (Adzuna)** — **DONE (2026-07-22).**
  Adzuna fetch → map → persist confirmed; `max_days_old=30` + `sort_by=date`
  recency fix applied; manual click-through of applyUrls passed (real live jobs).
- [x] **Phase 2 — Add More Sources + Deduplication** — **DONE (2026-07-23).**
  Jooble active (India fallback); Arbeitnow integrated but disabled; content-hash
  dedup confirmed (re-run saves 0); `V2` migration; `POST /api/fetch` orchestrates
  active sources with per-source counts. User confirmed.
- [x] **Phase 3 — Scheduler** — **DONE (2026-07-23).**
  `@EnableScheduling` + daily cron (06:00 IST, configurable via
  `FETCH_SCHEDULE_*` / `FETCH_KEYWORDS` / `FETCH_LOCATION`); logs
  new-jobs-per-run with a per-source breakdown and applies dedup. Verified firing
  under a fast test cron, and confirmed still registered on the 2026-07-27 boot.
- [x] **Phase 4 — Candidate profile** — **DONE (2026-07-27).** See below.
      Committed 2026-08-19 (the code had sat uncommitted since 07-25).
- [x] **Phase 5a — Deterministic rule engine** — **BUILT 2026-08-19, unverified.**
      `GET /api/matches` ranks stored listings against the profile across six
      weighted dimensions. See below.
- [ ] Phase 5b — LLM match explanations *(next; provider undecided)*
- [ ] Phase 6 — Telegram notifications
- [ ] Phase 7 — Application tracking
- [ ] Phase 8 — Demo polish (Docker/Adminer already done; Swagger, README, screenshots)

### Phase 4 verification (2026-07-27)

Code was written 2026-07-25 but the session ended before it was run or
committed. Verified on 2026-07-27 against a fresh container rebuild:

| Check | Result |
|---|---|
| Gradle build in container | ✅ BUILD SUCCESSFUL |
| Flyway `V3` migration | ✅ Applied; schema at v3 |
| App boot | ✅ Started 14.6s, healthy, no exceptions |
| `GET /api/profile` (unset) | ✅ 404 `profile_not_found` |
| `POST /api/profile` | ✅ 200, all fields persisted |
| `GET /api/profile` | ✅ 200, all four collections returned |
| `POST` again (upsert) | ✅ id/createdAt preserved, updatedAt bumped |
| Trim + dedupe of list values | ✅ `["  Java  ","Java","Kafka",""]` → `["Java","Kafka"]` |
| Bean validation (2 payloads) | ✅ 400 with per-field messages |
| `DELETE` | ✅ 204; child rows cascaded to 0 |
| Regression: health / jobs/count / scheduler | ✅ UP / 57 jobs / cron registered |

**Resolved risk:** four `EAGER` `@ElementCollection` bags on one entity did *not*
trigger `MultipleBagFetchException` — Hibernate 6 loads them via separate
selects, and `GET` works cleanly with `open-in-view=false`. No change needed.


### Phase 5a — rule engine (2026-08-19)

New package `com.jobdiscovery.scoring`, plus `GET /api/matches?limit&minScore&source`
and a `scoring.weights.*` block in `application.yml`.

| Dimension | Weight | Notes |
|---|---:|---|
| skills | 35 | Token-based; a title hit adds +0.15 |
| seniority | 25 | Stated years beat the title-implied level |
| location | 20 | City aliases + remote/hybrid |
| keywords | 10 | Profile keywords beyond skills |
| preferredCompany | 5 | Bonus only |
| recency | 5 | Decays over 60 days |

Design points worth remembering:

- **Token matching, not substring.** `contains("java")` matches "JavaScript";
  the engine compares token sequences instead, so it cannot. Plural-tolerant
  ("REST APIs" ↔ "REST API") and alternative-aware ("JPA/Hibernate").
- **Inapplicable dimensions drop out of the divisor.** A profile with no
  preferred companies is not marked down for it — otherwise every job would cap
  at 95. This is why the score divides by *applicable* weight, not total weight.
- **Over-qualification is penalised**, which is the fix for the seniority
  mismatch flagged below: a 10-year candidate against a "2-4 years" posting
  scores 0.1 on seniority, not 1.0.
- **Scoring is in-memory and on-demand**, with no stored scores. At a few hundred
  rows that is the right trade — re-tuning a weight re-scores everything for
  free with nothing to invalidate. Revisit if the table grows a lot.
- `CandidateProfile`'s no-arg constructor went from `protected` to `public` so
  the scoring tests can build one.

**First automated tests in the repo** (`src/test/java/com/jobdiscovery/scoring/`):
`TextNormalizerTest`, `SeniorityLevelTest`, `ExperienceRequirementTest`,
`JobScoringServiceTest` — all pure unit tests, no Spring context.

---

## 🧭 Key decisions

- **Everything runs in Docker** — no host JDK/Postgres. Postgres 18 + Adminer +
  app all containerised. (Docker pulled up from Phase 8 to Phase 0.)
- **Java 21 + Spring Boot 4.1.0** (latest stable, locked in over the original
  "3.x"). Gradle 9.5.1 via wrapper.
  - _SB4 gotchas hit:_ use per-tech starters (`spring-boot-starter-flyway`, not
    `flyway-core`); `RestClient.Builder` isn't auto-configured — use static
    `RestClient.builder()`.
- **PostgreSQL, not H2.**
- **Flyway migrations + `ddl-auto: none`** — schema versioned/explicit (V1 = table,
  V2 = content_hash + unique index, V3 = candidate profile + 4 child tables).
- **De-dup by content-hash** of normalised `(title|company|location)`, NOT by
  apply URL (URLs differ across sources / carry volatile tracking params). Unique
  index `ux_job_listing_content_hash`. Catches re-fetches AND cross-source dupes.
- **Per-source location handling** — Jooble uses a configurable fallback location
  (default "India") because it can't geocode Indian cities (see Known issues).
- **Profile is a singleton** — single-user tool, so `POST /api/profile` upserts
  the lowest-id row. The table still carries a surrogate id so it extends to
  multiple named profiles later without a schema change.
- **Profile list fields use `@ElementCollection` child tables**, not comma-joined
  columns — keeps them queryable and portable for Phase 5 scoring.
- **Real profile data stays out of git** — `my-profile.json` is git-ignored;
  `sample-profile.json` is the committed placeholder example. Salary expectation
  and notice period must never be committed.
- **Hybrid scoring (Phase 5):** deterministic rule engine produces the score; the
  LLM only *explains* it — never invents its own number.
- **Terminology:** "automation pipeline with AI-assisted scoring", NOT an "AI agent".
- **Target profile:** Java backend, Bangalore / India.
- **Git identity** — this repo has a **local** `user.name` / `user.email` in
  `.git/config` pointing at the personal account
  (`Balakrishna Bandaru <balakrishnab7@gmail.com>`). The global `.gitconfig` is a
  work identity; do not let it leak in. All 25 pre-Phase-4 commits were rewritten
  on 2026-07-27 to the personal identity (dates and content preserved).

---

## ⚠️ Known issues / flagged, not yet fixed

- **Seniority mismatch (partly addressed 2026-08-19).** The candidate has **10
  years** of experience, but the scheduler fetches on
  `FETCH_KEYWORDS="java developer"`, which surfaces many 2–5 year roles. The
  Phase 5a engine now penalises over-qualification heavily, so those roles rank
  low — but they are still being *fetched*. Widening `FETCH_KEYWORDS`
  (senior / lead / staff) is still open.
- **No salary in `JobListing` — salary is NOT scored.** The original Phase 5 plan
  listed "salary when present" as a dimension, but the entity has no salary
  column: Adzuna returns `salary_min`/`salary_max` and the mapper drops them.
  Adding it means a `V4` migration plus changes to all three source mappers, so
  it was left out of 5a rather than done silently. `expectedSalary` is therefore
  collected on the profile but unused.
- **Arbeitnow disabled** (resolved). Verified its API supports no keyword/location
  filtering (only pagination + `visa_sponsorship`). Disabled via
  `arbeitnow.enabled=false`; client code kept + documented for a possible future
  European search. Re-enable with `ARBEITNOW_ENABLED=true`.
- **Jooble can't geocode Indian cities** — `Bangalore`/`Bengaluru`/`Mumbai` → 0
  results; only the country `India` matches. We fall back to `India`, so Jooble
  results are India-wide (Java-relevant, e.g. Mastercard roles), not
  Bangalore-specific. Configurable via `JOOBLE_FALLBACK_LOCATION`.
- **Messy source text** — e.g. Adzuna "Corp" company, quoted titles. Stored as-is.
  The scoring engine normalises text *at match time* (`TextNormalizer`), which
  covers ranking, but the stored rows are still raw — so a digest or dashboard
  will show the messy strings.
- **No HTML sanitisation** of `description` (Adzuna/Arbeitnow may contain HTML).
  `TextNormalizer` strips tags for matching, but the stored column keeps them.
  Revisit before Phase 6 puts descriptions in front of a human.
- **No automated test for the profile or fetch endpoints.** Phase 5a added the
  first unit tests, but they cover the scoring package only — the web layer and
  the repositories are still verified by hand via `scripts/smoke-test.ps1`.
- **Cosmetic:** `expectedSalary` echoes as `4000000` on POST but `4000000.00` on
  GET (DB `numeric(12,2)` scale). Harmless; set the scale in the entity setter if
  consistent JSON is wanted.

---

## ▶️ Immediate next step (do this when you return)

**First: verify Phase 5a.** None of it has been compiled — Docker Desktop was
down for the whole 2026-08-19 session, so the scoring package, its unit tests and
the new smoke-test section are all unrun code.

```bash
docker compose up --build -d
docker build --target build -t jobdiscovery-build .
docker run --rm jobdiscovery-build ./gradlew --no-daemon test
.\scripts\smoke-test.ps1
```

Then eyeball `GET /api/matches?limit=10` against the ~57 stored jobs and sanity-
check the ranking by hand — the weights are a first guess and will need tuning
once there is real output to look at.

**Then Phase 5b — LLM match explanations:**

1. **Decide the provider** (Spring AI vs direct REST via `RestClient`) and add
   the key to `.env` — still unconfigured.
2. Feed the top-N `JobScore` records — score, matched/missing skills, seniority
   read, stated experience range — to the model and have it *explain* the match
   in a sentence or two. It must never produce or adjust the number; the
   evidence fields on `JobScore` exist precisely so it does not have to reason
   from the raw posting.
3. Cache or persist explanations if they get expensive — scoring is currently
   recomputed on every request, and an LLM call per job per request is not.

**Deferred idea — resume upload (discussed 2026-07-27).** Extract the profile
from an uploaded PDF/DOCX (Apache PDFBox + Apache POI). Agreed to defer until
*after* Phase 5 so it can reuse the Phase 5 LLM client for extraction rather than
brittle regex. Design agreed: **additive, not a replacement** — upload returns a
*draft* profile for review, and the existing JSON `POST /api/profile` still does
the saving. Note a resume only reliably supplies `skills`, `experienceYears` and
`keywords`; `preferredCompanies` / `preferredLocations` / `expectedSalary` /
`noticePeriodDays` are *preferences* a resume cannot provide (and past employers
must not be mistaken for preferred ones).

Useful endpoints: `POST /api/fetch?keywords=&location=` (all sources),
`POST /api/adzuna/import`, `GET /api/adzuna/search` (raw), `GET /api/jobs[?source=]`,
`GET /api/jobs/count`, `POST|GET|DELETE /api/profile`,
`GET /api/matches?limit=&minScore=&source=`.

---

## 🔑 API keys / config status

| Service | Status | Notes |
|---|---|---|
| **Adzuna** | ✅ working | App ID + Key in `.env`; live fetch confirmed |
| **Jooble** | ✅ working | Key in `.env`; returns India-wide results (city fallback) |
| **Arbeitnow** | ⏸️ disabled | Integrated but off (`arbeitnow.enabled=false`); no useful filtering for this search |
| **LLM API** (Phase 5) | ❌ not configured | Provider TBD (Spring AI vs direct REST) |
| **Telegram bot** (Phase 6) | ❌ not created | Create via BotFather at Phase 6 |

---
_Secrets live only in `.env` (git-ignored). This file records **status only**,
never key values. Personal profile data lives in `my-profile.json` (git-ignored)._
