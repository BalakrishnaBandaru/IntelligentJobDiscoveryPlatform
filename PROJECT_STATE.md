# Project State — Intelligent Job Discovery Platform

> Persistent hand-off doc so a fresh session can resume instantly without
> re-explaining the project. **Claude reads this first at the start of every
> session** and **updates it at the end of every phase or significant change.**
>
> _Last updated: 2026-08-20._

---

## 📍 Current phase

**Phase 5a — Deterministic rule engine — DONE and VERIFIED (2026-08-20).**
Built 2026-08-19 without ever being compiled (Docker Desktop was down that whole
session); verified 2026-08-20 against a full rebuild — see the table below.
Next up: **Phase 5b — LLM match explanations**, and a **weight-tuning pass**
(the first real ranking exposed a description-truncation problem — see Known
issues).

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
- [x] **Phase 5a — Deterministic rule engine** — **DONE (verified 2026-08-20).**
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

### Phase 5a verification (2026-08-20)

Verified against a full `docker compose up --build` rebuild. The code had never
been compiled before this run.

| Check | Result |
|---|---|
| Gradle build in container | ✅ BUILD SUCCESSFUL |
| Full test suite | ✅ **39 tests, 0 failures, 0 errors** (1 context-load + 38 scoring) |
| App boot | ✅ Healthy, DB UP, no exceptions |
| Flyway | ✅ Schema at v3, all migrations `success = t` |
| `GET /api/matches` (no profile) | ✅ 404 `profile_not_found` |
| `GET /api/matches?limit=5` | ✅ 200, ranked highest-first, all scores 0–100 |
| Six-dimension breakdown on every match | ✅ all six names present |
| `matchedSkills` / `missingSkills` / `applyUrl` | ✅ populated |
| `limit=0` / `minScore=101` | ✅ both return `[]` |
| `source=ADZUNA` / `source=jooble` | ✅ filters, and is case-insensitive |
| Determinism (same request twice) | ✅ identical jobId+score ordering |
| Regression: profile lifecycle | ✅ upsert, trim/dedupe, 400 validation, 204 delete |
| Regression: job count | ✅ 57 jobs (42 Adzuna / 15 Jooble) |

**26/26 API assertions passed.** The `smoke-test.ps1` script itself could not be
launched from the agent shell — `powershell.exe` is blocked by group policy on
this machine (see Known issues) — so the same assertions were re-run directly
against the API. **Run `.\scripts\smoke-test.ps1` by hand to exercise the
script itself**, including the live-fetch section that was skipped here to
preserve the daily Adzuna/Jooble quota.

**Correction to the test command.** The previously documented
`docker run --rm jobdiscovery-build ./gradlew --no-daemon test` **does not
work**: `JobDiscoveryPlatformApplicationTests` is a `@SpringBootTest` and needs a
reachable database, which a bare `docker run` has no network route to. Use the
compose `test` profile, which joins the network and waits for a healthy db:

```bash
docker compose --profile test run --rm test
```

**First real ranking (57 jobs, real profile).** Top match 70.5 — *Java / J2EE
Tech Lead* @ PayU, Bangalore. Scores spanned 28.8–70.5, median 49.2. Seniority,
location and recency all behaved as designed; the over-qualification penalty and
the country-only location fix (`fe17ace`) both fired correctly on real rows. The
skills dimension did **not** — see the truncation issue below.

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
- **🔴 Adzuna descriptions are truncated to exactly 500 characters, which
  starves the heaviest scoring dimension.** Found 2026-08-20 on the first real
  ranking. Every one of the 42 Adzuna rows has `length(description) = 500`
  exactly (Jooble averages 313) — the API returns a snippet, not the posting.
  The `skills` dimension is weighted 35, the highest of the six, but it is
  matching against that snippet. Across all 57 listings:

  | Skills matched (of 8) | Listings |
  |---:|---:|
  | 0 | 25 |
  | 1 | 20 |
  | 2 | 11 |
  | 4 | 1 |

  No listing matches more than **4 of 8** skills. `Docker` and `PostgreSQL`
  match **0/57**; `Kafka` and `MySQL` match 1; even `Java` matches only 31/57 —
  on a search whose keyword *was* "java developer". So a low skills score
  currently means "the snippet was cut off", not "the job does not want this
  skill", and the top score is capped around 70. Fix directions, in order of
  value: (a) fetch the full description from the posting URL, or find a fuller
  field in the Adzuna response; (b) score skills against title + snippet but cap
  the *penalty* for unmatched skills when the description is known-truncated;
  (c) reduce the skills weight until (a) is possible. **Do this before tuning
  any other weight** — the current numbers are measuring truncation.
- **Skills scoring divides by the profile's skill count**, so listing more skills
  lowers every score. An 8-skill profile is structurally penalised against a
  3-skill one. Compounds the truncation issue above. Consider scoring against the
  *matched* skills' importance rather than a flat fraction.
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
- **Cosmetic, same class:** an *insert* response echoes `createdAt`/`updatedAt`
  from the in-memory `Instant` at nanosecond precision
  (`...266480283Z`), while every read-back comes from Postgres `timestamp(6)` at
  microsecond precision (`...266480Z`). Same instant, different digits. Confirmed
  2026-08-20 that upsert semantics are correct — id and `createdAt` *are*
  preserved; only the echoed precision differs.
- **`powershell.exe` is blocked by group policy on this machine** (work laptop),
  so the agent cannot run `scripts/smoke-test.ps1` itself — both a direct spawn
  and a `cmd.exe` wrapper are refused. **The user must run the smoke test by
  hand**; the agent can only re-create its assertions with `curl`/`python`
  against the API. Not a project defect, but it changes the verification loop.
- **Backup/restore of the profile does not preserve `id` or `createdAt`.** The
  smoke test (and any DELETE-then-POST cycle) creates a *new* row, so the
  profile comes back with fresh identity. All user-meaningful fields survive and
  the singleton invariant holds (1 row), so this is cosmetic for a single-user
  tool — but do not treat profile `id` as stable across a smoke-test run.

---

## ▶️ Immediate next step (do this when you return)

**Phase 5a is verified — that blocker is cleared.** To bring the stack up and
re-run the suite:

```bash
docker compose up --build -d              # stack: db + adminer + app
docker compose --profile test run --rm test   # 39 tests (needs the db — see above)
```

```powershell
.\scripts\smoke-test.ps1                  # run this yourself; agent is blocked by group policy
```

**First: fix the description truncation, then tune the weights.** The first real
ranking showed the `skills` dimension (weight 35, the heaviest) is scoring
against a 500-character Adzuna snippet rather than the posting — `Docker` and
`PostgreSQL` match 0 of 57 rows. See the red item under Known issues. Tuning any
weight before fixing this is tuning against an artefact.

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
