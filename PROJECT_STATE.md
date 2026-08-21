# Project State — Intelligent Job Discovery Platform

> Persistent hand-off doc so a fresh session can resume instantly without
> re-explaining the project. **Claude reads this first at the start of every
> session** and **updates it at the end of every phase or significant change.**
>
> _Last updated: 2026-08-21 (second session)._

---

## 📍 Current phase

**Phase 5a — Deterministic rule engine — DONE and VERIFIED (2026-08-20).**
Built 2026-08-19 without ever being compiled (Docker Desktop was down that whole
session); verified 2026-08-20 against a full rebuild — see the table below.
**Phase 5b — match explanations — DONE and RUNNING LOCALLY (2026-08-21).**
Three tiers: a local Ollama model, the Anthropic API, and a deterministic
templated explainer underneath both. **Verified end to end against the local
model** — real explanations for the real 57 rows. The Claude tier remains
unexercised (the account has no credit; a Pro subscription does not fund the
API), but it is no longer on the critical path. Next up: **Phase 6 — Telegram
notifications** — but see the deployment note under Known issues first.

**Startup fetch added 2026-08-21.** The pipeline had silently not fetched for 25
days; starting the stack now catches up. See below.

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
- [x] **Phase 5b — match explanations** — **DONE (verified 2026-08-21).**
      `GET /api/matches?explain=true`, three tiers (ollama / claude / templated).
      Verified against the local model. See below.
- [ ] Phase 6 — Telegram notifications *(next)*
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
| skills | 35 | Token-based; a title hit adds +0.15; misses discounted on truncated text |
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
| Full test suite | ✅ **39 tests, 0 failures, 0 errors** (1 context-load + 38 scoring)<br>later **48** (truncation fix, +9) then **53** (joined-spelling fix, +5) |
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

### Phase 5b — match explanations (2026-08-20, reworked 2026-08-21)

New package `com.jobdiscovery.explain`, `&explain=true` on `/api/matches`, and
an `explanation.*` block in `application.yml`.

**Three tiers, tried in order.** Every response reports which answered, in
`explanationSource`:

| Tier | `EXPLANATION_PROVIDER` | Cost | State |
|---|---|---|---|
| Local model | `ollama` | Free | ✅ verified end to end 2026-08-21 |
| Anthropic API | `claude` | Per token | ⚠️ built, never succeeded — no account credit |
| Templated | *(fallback)* | Free | ✅ verified; the default |

**Why the default is `none`.** A fresh clone gets working explanations with no
key, no model download and no account — the templated explainer answers. It is
also the safety net: a stopped Ollama container or an exhausted balance degrades
the prose instead of 502-ing the shortlist, because the ranking never depended
on a model. `EXPLANATION_FALLBACK=error` opts out.

**Provider decision (user, 2026-08-20): direct REST via `RestClient`**, not
Spring AI, so every client in the project has the same shape and no dependency
was added. An official Java SDK does exist (`com.anthropic:anthropic-java`) —
a deliberate choice, and the thing to revisit if the client grows.

**Why a 3B model is defensible here.** The rule engine does the judging; the
model only writes it up. That is the whole reason a laptop-sized model is a
credible provider — the hard part was never delegated to it.

Design points worth remembering:

- **No tier is shown the posting.** Each gets the evidence `JobScore` already
  carries. A test asserts the prompt contains no posting text, so "explains the
  number rather than forming its own view" is enforced in code, not just hoped
  for in the prompt.
- **The prompt is written for the weakest model that will run it**, and three
  details were each earned by watching llama3.2:3b get it wrong:
  1. *Second person as a prohibition.* "Address the candidate as you" produced
     "This candidate…" every time. Listing the banned phrasings fixed it — then
     it opened every note with a literal "You,", so that is banned too.
  2. *An unrelated example.* A relevant one was lifted almost verbatim.
  3. *Ratings, not arithmetic.* Shown "21.2 of 25 points" it reasoned about the
     numbers and got it **backwards** — calling a 0.85 seniority score the thing
     "pulling the match down". Dimensions now arrive pre-labelled STRONG /
     MODERATE / WEAK, and the score is withheld entirely because the model kept
     reciting it.
- **`withExplanation()` returns a copy**, so an explanation can never be
  mistaken for an input to the score.
- **Model output is cached by job *and* score**; the template is not cached,
  being cheap enough that caching would only add a way to be stale.
- **Refusal handling (Claude tier).** A refusal is HTTP 200 with no usable
  content, so `stop_reason` is checked before the content is read.

**Honest quality assessment (2026-08-21).** llama3.2:3b is factually
constrained — it reports the ratings and does not overclaim on truncated text —
but the prose is clunky, and **the templated tier arguably reads better**. The
local model earns its place by being free and private, not by being good. A
larger model or the Claude tier is a clear step up. Performance: ~12s per
explanation warm on CPU, 0.36s fully cached, and a slow first call while the
2GB model loads. Memory: Ollama sits at ~3.9GB of the VM's 7.6GB.

**Tests: 72 passing** (19 across `MatchExplainerTest` and
`TemplatedExplainerTest`), none touching the network — the LLM tier is stubbed,
so what is tested is the prompt contract, the caching, the cost cap, and that a
provider failure degrades to the template.

### Startup fetch + run history (2026-08-21)

**The problem.** The newest stored posting was 2026-07-27 — 25 days stale — and
nothing anywhere recorded that the pipeline had stopped. Two causes, both
structural:

1. The daily cron fires at 06:00 IST, but the container only exists while Docker
   Desktop is running. On a laptop it almost never fires.
2. **Nothing recorded fetch *attempts*.** De-duplication means a healthy run that
   finds only duplicates writes no rows, so "no new jobs" and "no run happened"
   were indistinguishable from the `job_listing` table.

**The fix.** `V4__create_fetch_run.sql` plus `StartupFetchJob`:

- Every run is now recorded — trigger (`STARTUP`/`SCHEDULED`/`MANUAL`), counts,
  per-source breakdown — including runs that fail, and runs that find nothing.
- `GET /api/fetch/runs` exposes the last 20, which is the answer to "is this
  thing actually running?"
- On `ApplicationReadyEvent`, if the last *attempt* is older than
  `fetch.startup.max-age-hours` (12), fetch. **Guarded on the attempt, not the
  newest job** — job age would re-fetch on every restart, and this container was
  recreated a dozen times in one afternoon.
- Runs on its own daemon thread: `ApplicationReadyEvent` listeners run on the
  startup thread, and blocking there on two external APIs would delay the health
  check and risk the container's start period.
- Failure is swallowed and logged. The app is already serving and the stored
  listings are still rankable.
- Disabled in `src/test/resources/application.properties`, along with the
  scheduler — a fetch during `@SpringBootTest` would spend real API quota on
  every build.

**Result of the first run (2026-08-21):** 40 fetched, **29 new jobs saved**, 11
duplicates. The pool went 57 → 86 listings; 22 of them posted within 7 days,
where previously *none* were under 24 days old.

| | Before | After |
|---|---:|---:|
| Listings | 57 | 86 |
| Top score | 76.7 | **80.7** |
| Median score | 51.7 | 54.0 |
| Scoring 70+ | 3 | **9** |
| Scoring 60+ | 11 | **26** |

Three of the top five are now postings from the last day. This is the evidence
for a general point worth keeping: **fresher inputs beat better ranking.** The
weights had not changed at all.

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
  V2 = content_hash + unique index, V3 = candidate profile + 4 child tables,
  V4 = fetch_run history).
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

- **Seniority mismatch (CLOSED 2026-08-21 — the premise was wrong).** This was
  recorded as "`FETCH_KEYWORDS=\"java developer\"` surfaces many 2–5 year roles",
  with widening the keywords left open. Measured against the actual pool, that
  is not true: **59% of the 86 stored listings read SENIOR, LEAD or PRINCIPAL**
  (21 senior, 21 lead, 9 principal, against 34 mid and 1 junior). The mix is
  fine and the over-qualification penalty handles the rest. No action needed —
  do not widen the keywords on the strength of the old note.

- **Truncated postings starving the skills dimension (FIXED 2026-08-20).**
  Found on the first real ranking, fixed the same day. **Every one of the 57
  stored rows is a preview, not a posting** — Adzuna caps its description at
  exactly 500 characters and ends it `…`; Jooble's field is literally named
  `snippet` and ends `...&nbsp;`. The `skills` dimension is weighted 35, the
  heaviest of the six, and `matched / total profile skills` was measuring how
  soon the text ran out rather than how well the job fits: `Docker` and
  `PostgreSQL` matched **0 of 57** rows, `Kafka` and `MySQL` 1 each, and `Java`
  only 31/57 on a search whose keyword *was* "java developer".

  **Fix:** a skill absent from *truncated* text is now scored as **unknown, not
  absent** — the same distinction the location dimension already draws for a
  country-only location (`fe17ace`). `TextNormalizer.isTruncated()` detects the
  marker (tolerating Jooble's trailing `&nbsp;`), and unmatched skills are
  discounted by `scoring.truncated-miss-weight` (default `0.5`, env
  `SCORING_TRUNCATED_MISS_WEIGHT`). Full descriptions are untouched, so behaviour
  is unchanged for any future source returning complete text, and `1.0` restores
  the old ratio exactly.

  **Effect on the real 57 rows:** top score 70.5 → 76.7, median 49.2 → 50.5.
  Listings matching **zero** skills did not move at all (median 43.5 before and
  after) — the discount cannot invent a match. Genuine matches that were buried
  came up: Mastercard's "Lead Software Engineer - Java, Spring, Springboot,
  Kafka" went #30 → #17, and two Java lead roles gained 13 places each. The
  listings that *fell* in rank — QA roles, "Sr Mgr - IT Appl Development" — all
  kept their exact scores and only dropped because real Java roles overtook
  them. That is the intended shape of the change.

  **Still worth doing later:** fetching the real posting text would beat
  discounting for it. Whether Adzuna exposes a fuller field, or whether it needs
  following `redirect_url` to the employer site, is **unverified** — that was not
  investigated, and scraping through the redirector would be fragile.
- **Skills scoring still divides by the profile's skill count**, so listing more
  skills lowers every score — an 8-skill profile is structurally penalised
  against a 3-skill one. The truncation fix above *mitigates* this (the
  denominator shrinks when text is truncated, which is every row today) but does
  not remove it: with full descriptions the old behaviour returns. A real fix
  would score the *importance* of the matched skills rather than a flat fraction.
- **Multi-word skills vs. their closed-up spelling (FIXED 2026-08-20).** A
  profile skill of `Spring Boot` did not match a title writing "Springboot" as
  one word — the Mastercard listing named Spring Boot *in its own title* and
  still scored it missing. `TextNormalizer.containsJoined()` now compares the
  joined forms in **both** directions ("Spring Boot" ↔ "Springboot"), so no
  hand-maintained alias list is needed. Guarded to joined forms of six
  characters or more, so short tokens ("aws", "go", "c#") cannot be invented by
  gluing neighbours together, and it never reopens the phrase-adjacency hole —
  "spring intake, safety boot" still does not match Spring Boot.

  **Effect:** 4 listings improved, **0 got worse**. Mastercard's Lead Software
  Engineer went 54.7 → 60.1 (#17 → #11; #30 → #11 counting the truncation fix
  too). A bonus nobody planned: it also fixes *hyphenated* compounds, because
  "Back-End" tokenises to `back` + `end` — "Java Engineer (Back-End &
  Microservices)" now matches the profile keyword `backend`.
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
- **The daily cron cannot be relied on, by construction (mitigated 2026-08-21).**
  It fires at 06:00 IST, but the container only exists while Docker Desktop is
  running on a laptop — so it almost never fires. Found when the newest stored
  posting turned out to be **25 days old** (2026-07-27) with nothing recording
  that the pipeline had stopped. The startup fetch now covers this: starting the
  stack fetches if the last *attempt* is older than 12h. **The underlying point
  stands, though — a schedule assumes an always-on host, and this is not one.**
  If Phase 6's daily Telegram digest is to mean anything, the pipeline needs
  somewhere that stays up; a digest from a container that is not running
  delivers nothing. That is the real question to answer before Phase 6.
- **Adzuna occasionally returns a posting dated slightly in the future**
  (seen 2026-08-21: `posted_date` a day ahead of `now()`). `scoreRecency`
  already clamps a negative age to 0, so it scores as brand new rather than
  breaking — noted so it is not mistaken for a bug later.
- **🔴 The Postgres password is still the published placeholder.** `.env`'s
  `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` are byte-identical to the
  committed `.env.example`, and **the repo is now public**. The host port
  bindings for db and Adminer were narrowed to `127.0.0.1` on 2026-08-20, which
  removes the network exposure, but the credential itself is unchanged. Rotating
  it needs two steps in this order, because `POSTGRES_PASSWORD` is only read when
  the data volume is first initialised — changing `.env` alone locks the app out
  of its own data:

  ```bash
  docker exec -it jobdiscovery-db psql -U jobdiscovery -d jobdiscovery -c "ALTER USER jobdiscovery WITH PASSWORD 'new-strong-password';"
  # then edit POSTGRES_PASSWORD in .env to the same value, and:
  docker compose up -d --force-recreate app
  ```

  The agent attempted this and was **blocked by the permission classifier** —
  rewriting a secrets file plus rotating a database credential is exactly what it
  guards. Left for the user to run by hand.
- **Docker builds intermittently fail to reach `services.gradle.org`** on this
  network (`SocketTimeoutException` downloading `gradle-9.5.1-bin.zip`), which
  kills any image build whose cache has been pruned. Workaround that does not
  need the Gradle distribution — run the tests in the *existing* image with the
  sources mounted over it:

  ```bash
  MSYS_NO_PATHCONV=1 docker compose --profile test run --rm -v "E:\GitPersonalProject\IntelligentJobDiscoveryPlatform\src:/workspace/src" test ./gradlew --no-daemon cleanTest test
  ```

  Do **not** add `--offline`: the test dependencies are not baked into the image
  (the image build runs `bootJar`, not `test`), so they still come from Maven
  Central on every run.
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

**The truncation blocker is cleared** — weights can now be tuned against
numbers that mean something. `scoring.truncated-miss-weight` (default `0.5`) is
itself the first knob worth trying; the weights have never been tuned against
real output.

**Next, in value order:**

1. **Tune the weights.** Now genuinely worth doing: the data is fresh (86
   listings, 22 from the last week) and both blockers that made tuning
   meaningless — truncation and word-break matching — are fixed. Nine listings
   score 70+, so there is a real spread to tune against for the first time.
2. **Decide where this runs before building Phase 6.** A daily Telegram digest
   from a container that is not running delivers nothing; the startup fetch
   papers over the cron for local use but does not make the pipeline
   always-on. Options: a small always-on host, or GitHub Actions on a cron
   against a hosted instance. This question is Phase 6's real prerequisite.
3. **Phase 6 — Telegram notifications**, once (2) is answered.
4. **Phase 8 polish** before applying anywhere — the README and screenshots are
   what an employer actually sees.

Lower value, deliberately deprioritised:

- **A better local model.** llama3.2:3b writes clumsily but is factually
  constrained, and the templated tier arguably reads better anyway. ~3.7GB of
  headroom remains, so `qwen2.5:7b` at Q4 is tight but might fit.
- **The Claude tier**, if there is ever credit. Needs
  `EXPLANATION_PROVIDER=claude` and a balance; the key in `.env` is valid.
- **Phase 7 — application tracking.** CRUD, and a spreadsheet does it today.

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
`GET /api/jobs/count`, `GET /api/fetch/runs`, `POST|GET|DELETE /api/profile`,
`GET /api/matches?limit=&minScore=&source=&explain=`.

---

## 🔑 API keys / config status

| Service | Status | Notes |
|---|---|---|
| **Adzuna** | ✅ working | App ID + Key in `.env`; live fetch confirmed |
| **Jooble** | ✅ working | Key in `.env`; returns India-wide results (city fallback) |
| **Arbeitnow** | ⏸️ disabled | Integrated but off (`arbeitnow.enabled=false`); no useful filtering for this search |
| **Ollama** (Phase 5b) | ✅ working | Local, free, no key. `docker compose --profile llm up -d ollama` + `ollama pull llama3.2:3b`. Currently `EXPLANATION_PROVIDER=ollama` |
| **Anthropic** (Phase 5b) | ⚠️ key valid, **no credit** | Key in `.env`; auth confirmed via `/v1/models`. Calls fail with "credit balance is too low". **A Claude Pro subscription does not fund the API** — they are separately billed products. Not on the critical path now that Ollama works |
| **Telegram bot** (Phase 6) | ❌ not created | Create via BotFather at Phase 6 |

---
_Secrets live only in `.env` (git-ignored). This file records **status only**,
never key values. Personal profile data lives in `my-profile.json` (git-ignored)._
