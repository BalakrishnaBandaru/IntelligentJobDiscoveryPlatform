# Intelligent Job Discovery Platform

> An **automation pipeline with AI-assisted scoring** for a Java-developer job
> search. This is **not** an autonomous "AI agent" — it is a deterministic
> aggregate → deduplicate → rank → notify pipeline. An LLM is used only to
> *explain* a score that the deterministic rule engine produced; it never
> invents its own score.

A Spring Boot service that aggregates job listings from multiple public APIs
(Adzuna, Jooble, Arbeitnow), deduplicates them, ranks them with a configurable
rule engine, uses an LLM to explain each shortlisted match, and delivers
filtered recommendations via Telegram — with personal application tracking
built in.

> **Status:** all eight phases complete. Runs entirely in Docker; the only
> things it will not do without credentials are send a Telegram message and call
> a paid LLM, and both degrade rather than fail.

---

## Why this exists (problem statement)

Manually checking several job portals every day is slow, repetitive, and easy to
let slip. Listings are scattered, full of duplicates, and most are a poor fit.
This tool runs the daily *fetch → dedupe → rank → notify* loop automatically, so
the only thing a human looks at is a short, ranked, explained shortlist.

## Progress

| Phase | Scope | Status |
|------:|-------|:------:|
| 0 | Project + Docker scaffold; Postgres connectivity | ✅ Done |
| 1 | Fetch jobs (Adzuna first) | ✅ Done |
| 2 | More sources (Jooble, Arbeitnow) + dedupe + Flyway | ✅ Done |
| 3 | Daily scheduler | ✅ Done |
| 4 | Candidate profile | ✅ Done |
| 5a | Rule engine (deterministic scoring) | ✅ Done |
| 5b | LLM match explanations | ✅ Done |
| 6 | Telegram notifications | ✅ Done |
| 7 | Application tracking | ✅ Done |
| 8 | Demo polish (Swagger, README, screenshots) | ✅ Done |

## Tech stack

- **Java 21** (LTS)
- **Spring Boot 4.1.x** — Web MVC, Data JPA, Validation, Actuator
- **PostgreSQL 18**
- **Flyway** for DB migrations — six versioned migrations, `ddl-auto: none`
- **springdoc-openapi** for the Swagger UI, generated from the controllers
- **Ollama** (optional) for local, free match explanations
- **Gradle** (wrapper-pinned)
- **Docker + Docker Compose** — Postgres, Adminer, and the app all run in
  containers; no JDK or Postgres install is required on the host to run it

## Architecture

```
                 +-------------+   +-------------+   +-------------+
  External APIs  |   Adzuna    |   |   Jooble    |   | Arbeitnow   |
                 +------+------+   +------+------+   +------+------+
                        \                 |                 /
                         \                |                /
                          v               v               v
                     +--------------------------------------+
                     |          Fetch + normalise            |
                     +---------------------+-----------------+
                                           v
                     +--------------------------------------+
                     |   Deduplicate  ->  Persist (Postgres) |
                     +---------------------+-----------------+
                                           v
                     +--------------------------------------+
                     |  Rule engine (deterministic score)    |
                     |  six weighted dimensions -> 0-100     |
                     +---------------------+-----------------+
                                           v
                     +--------------------------------------+
                     |  Explain the score (never change it)  |
                     |  local LLM -> template -> error       |
                     +---------------------+-----------------+
                                           v
                     +--------------------------------------+
                     |   Telegram digest  ->  you apply      |
                     +---------------------+-----------------+
                                           v
                     +--------------------------------------+
                     |  Application tracker                  |
                     |  feeds back: applied jobs drop out    |
                     +--------------------------------------+
```

The feedback edge at the bottom is the part worth noticing: once a job is
tracked, the digest stops raising it and the shortlist marks it, so the list
stays things you have not yet decided about.

## Quick start

**Prerequisites:** Docker Desktop. Nothing else — no JDK, no Postgres, no Gradle
on the host. (A local JDK 21 only helps IDE completion; the build happens in a
container.)

```bash
cp .env.example .env          # PowerShell: Copy-Item .env.example .env
# Add your free Adzuna keys (https://developer.adzuna.com/) to .env
docker compose up --build -d
```

That is the whole setup. On startup the app fetches if its data is stale, so you
have ranked results within a minute or two:

```bash
curl "http://localhost:8080/api/matches?limit=5"          # ranked shortlist
curl "http://localhost:8080/api/matches?limit=5&explain=true"   # with reasons
```

| What | Where |
|---|---|
| **API docs (Swagger UI)** | http://localhost:8080/swagger-ui/index.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Database browser (Adminer) | http://localhost:8081 — server `db`, credentials from `.env` |

`docker compose down` stops it; add `-v` to wipe the database volume too.

### What works without any API keys

Everything except fetching. The scoring engine, the templated explanations, the
digest preview and the tracker all run against whatever is in the database. Only
Adzuna and Jooble need credentials, and only to bring new listings in.

### Optional extras

```bash
docker compose --profile llm up -d ollama                     # free local LLM
docker exec jobdiscovery-ollama ollama pull llama3.2:3b
docker compose --profile test run --rm test                   # the test suite
```

## API documentation

Swagger UI is served at **http://localhost:8080/swagger-ui/index.html**, grouped
in pipeline order — Fetch, Jobs, Profile, Matches, Notifications, Applications.

The spec is generated from the controllers by
[springdoc](https://springdoc.org/), so it cannot drift out of step with the code
the way a hand-written one does. Only the title, description and tag ordering are
declared, in [`OpenApiConfig`](src/main/java/com/jobdiscovery/web/OpenApiConfig.java).

## A worked example

Real output from a running instance, 86 stored listings.

**A fetch happened, and it is on the record** — the run log exists precisely so
"nothing new" and "nothing ran" cannot look the same:

```
GET /api/fetch/runs
2026-08-21T03:46  STARTUP   fetched=40  saved=29  dup=11
```

**The top match, and why** — every score comes with the arithmetic behind it:

```
GET /api/matches?limit=1

80.7  Senior Computer Scientist
Adobe | Bangalore, Karnataka | ADZUNA
  skills            0.67  matched 4 of 8 skills: Java, Spring Boot, Microservices, AWS
  seniority         1.00  title reads SENIOR, matches the SENIOR level implied by 10 years
  location          1.00  'Bangalore, Karnataka' matches preferred 'Bangalore'
  keywords          0.33  matched 1 of 3 keywords: senior
  preferredCompany  n/a   profile states no preferred companies
  recency           1.00  posted 0 day(s) ago
```

`preferredCompany` reading `n/a` is the design working: the profile names no
preferred employers, so rather than scoring zero and capping every job at 95,
the dimension drops out of the divisor entirely.

**The digest that would go out**, without needing a bot token:

```
GET /api/notify/preview

<b>8 new matches</b>

<b>80.7</b> · <a href="...">Senior Computer Scientist</a>
Adobe · Bangalore, Karnataka
<i>A strong match at 80.7 out of 100, mainly because the seniority fits. It names
Java, Spring Boot, Microservices, AWS from your profile, and does not mention
PostgreSQL, MySQL, Kafka and 1 other — though the posting text is cut short, so
those may still be wanted.</i>
```

**Track it, and it leaves the queue:**

```
POST /api/applications  {"jobId": 172}
→ 200  status APPLIED

GET /api/notify/preview
→ top entry is now 76.7 — the tracked job is gone
```

## Environment variables

All configuration is supplied via environment variables — see
[`.env.example`](.env.example). Real values live in `.env`, which is git-ignored
and never committed.

## Candidate profile (Phase 4)

The profile is the target that Phase 5 scores jobs against. This is a
single-user tool, so there is exactly one profile: `POST` creates it, or
replaces it in place if one already exists.

| Method | Path | Behaviour |
|---|---|---|
| `POST` | `/api/profile` | Create or replace the profile; returns the saved entity |
| `GET` | `/api/profile` | The current profile; `404` if none is set |
| `DELETE` | `/api/profile` | Clear the profile (handy for re-seeding) |

Only `skills` (at least one) and `experienceYears` (≥ 0) are required —
everything else is optional. List values are trimmed, and blanks and duplicates
are dropped, before persisting.

```bash
curl -X POST http://localhost:8080/api/profile \
  -H "Content-Type: application/json" \
  --data-binary @sample-profile.json
```

[`sample-profile.json`](sample-profile.json) is a placeholder example. Keep your
real profile in `my-profile.json`, which is git-ignored so personal details
(salary expectation, notice period) never enter the repository.

## Ranked matches (Phase 5a)

Every stored listing is scored against the candidate profile by a
**deterministic rule engine**. The score is produced by code — the LLM layer in
Phase 5b only puts the result into words, and can neither produce nor adjust the
number. That split is what keeps the ranking auditable and reproducible.

| Method | Path | Behaviour |
|---|---|---|
| `GET` | `/api/matches?limit=20&minScore=0&source=` | Ranked shortlist, highest score first; `404` if no profile is set |

Six dimensions contribute. The weights are tunable at runtime — set any of them
in `.env` and recreate the app container, no rebuild:

```bash
SCORING_WEIGHT_SKILLS=40 docker compose up -d --force-recreate app
```

| Dimension | Weight | What it measures |
|---|---:|---|
| `skills` | 35 | Profile skills the posting names; a skill in the **title** counts for more than one buried in the body |
| `seniority` | 25 | Years the posting asks for vs. the candidate's, falling back to the level implied by the title |
| `location` | 20 | Posting location vs. preferred locations, with city aliases and remote/hybrid handling. A country-only location (all a source can give for some markets) has no city to judge, so the dimension drops out rather than guessing |
| `keywords` | 10 | Profile keywords beyond the hard skills list |
| `preferredCompany` | 5 | Bonus when the employer is one the candidate named |
| `recency` | 5 | How recently the posting went up |

Four design decisions worth calling out:

- **Matching is token-based, not substring-based.** A `contains("java")` check
  matches "JavaScript" and would score a front-end role as a Java match. Skills
  are compared as token sequences, so phrases match as phrases and `Java` never
  matches `JavaScript`.
- **Word breaks are ignored, in both directions.** `Spring Boot` matches a
  posting that writes "Springboot", and a profile listing `Springboot` matches
  one that writes "Spring Boot" — the joined forms are compared, so no alias
  list needs maintaining. It also handles hyphenated compounds, so "Back-End"
  matches `backend`. Guarded to joined forms of six characters or more, so short
  tokens (`aws`, `go`, `c#`) can never be manufactured by gluing neighbouring
  words together.
- **A dimension with nothing to judge drops out** rather than scoring zero. If
  the profile names no preferred companies, that weight leaves the divisor
  instead of capping every job at 95. The same applies to a posting whose
  location is only a country — which turned out to matter more than expected.
  Jooble cannot geocode Indian cities and returns the literal string "India" for
  every result, so scoring it *anything* fixed turned a 20-weight dimension into
  a flat penalty on one source: every Adzuna row scored 1.00 and every Jooble row
  0.50, putting 95% of the top 20 on one source while the pool was 72/28.
  Dropping out lets those postings be judged on evidence that exists.
- **A missing skill in a truncated posting is *unknown*, not *absent*.** Neither
  source returns the real posting — Adzuna caps its description at 500 characters
  and Jooble's field is a `snippet`, so every stored row is a preview. Counting
  the unmentioned skills as full misses measured how soon the text ran out rather
  than how well the job fits. Unmatched skills are therefore discounted when the
  text is truncated, controlled by `scoring.truncated-miss-weight` (default
  `0.5`; set `1.0` to treat previews as complete text). Matching zero skills
  still scores zero — the discount never invents a match.

Each match returns its full breakdown — per-dimension score, matched skills,
missing skills, the seniority read off the title, and any experience range found
in the text. Those fields are the evidence the Phase 5b LLM will be given to
explain, rather than being asked to reason from the raw posting.

```bash
curl "http://localhost:8080/api/matches?limit=5"
```

### Match explanations (Phase 5b)

Add `&explain=true` to have the top matches explained in prose. **No tier
produces or adjusts the score.** Each is handed the number and the evidence the
rule engine already derived — matched and missing skills, the seniority read,
the per-dimension rating — and asked only to phrase it. None is ever shown the
job posting, which is exactly why none can form its own opinion of the fit.

```bash
curl "http://localhost:8080/api/matches?limit=5&explain=true"
```

Three tiers, tried in order. Every response says which one answered, in
`explanationSource`:

| Tier | `EXPLANATION_PROVIDER` | Cost | Notes |
|---|---|---|---|
| Local model | `ollama` | Free | Runs in a container; nothing leaves the machine |
| Anthropic API | `claude` | Per token | Needs a prepaid balance — a Claude Pro subscription does **not** fund it |
| Templated | *(fallback)* | Free | Deterministic prose from the same evidence. Always available |

**The default is `none`, so this works with no key and no download** — the
templated explainer answers. That is also the safety net: if Ollama is stopped
or an API balance runs out, the shortlist still comes back with explanations
rather than a 502, because the ranking never depended on a model. Set
`EXPLANATION_FALLBACK=error` if you would rather see the outage.

#### Running the local model

```bash
docker compose --profile llm up -d ollama
docker exec jobdiscovery-ollama ollama pull llama3.2:3b
# then set EXPLANATION_PROVIDER=ollama in .env and recreate the app
docker compose up -d --force-recreate app
```

It sits behind the `llm` profile so a plain `docker compose up` stays light. A
3B model is enough because of how the work is split — the rule engine did the
judging, so the model only writes it up. Expect roughly 12s per explanation on
CPU once warm, plus a slow first call while the model loads into memory.

**A caveat worth stating plainly:** at 3B the prose is serviceable but clunky,
and arguably the *templated* tier reads better. The local model earns its place
by being free and private, not by being good. A larger model, or the Claude
tier, is a clear step up if you have the memory or the credit.

Model explanations are cached in memory, keyed by job **and** score, so
re-running a demo is instant and free after the first call; changing a weight
changes the score and expires the entry by itself.

### Keeping the data fresh

The daily cron (`fetch.schedule.*`) assumes a host that stays up. A laptop
running Docker Desktop is not one — in practice the container is rarely alive at
06:00, and the pipeline once went **25 days without fetching** while nothing
recorded that it had stopped.

Two things address that:

- **A catch-up fetch on startup.** If the last attempt is older than
  `FETCH_STARTUP_MAX_AGE_HOURS` (default 12), starting the stack triggers a
  fetch. It runs on a background thread so the health check is not delayed, and
  a failure is logged rather than propagated — the stored listings are still
  rankable. Disable with `FETCH_STARTUP_ENABLED=false`.
- **A record of every run**, at `GET /api/fetch/runs`. This exists because the
  job table could not answer "is the pipeline running?" — de-duplication means a
  healthy run that finds only duplicates writes no rows, so a stalled pipeline
  and a quiet one looked identical.

```bash
curl "http://localhost:8080/api/fetch/runs"
```

The guard keys on the last *attempt*, not the newest job, precisely so that a
run which legitimately finds nothing new does not cause a re-fetch on every
restart.

## Telegram digest (Phase 6)

Sends the shortlist to a Telegram chat — after the scheduled fetch, after the
startup fetch, or on demand.

| Method | Path | Behaviour |
|---|---|---|
| `POST` | `/api/notify?force=false` | Send a digest of matches not sent before |
| `GET` | `/api/notify/preview` | The exact message it *would* send, without sending — **works with no bot configured** |

### Setup

1. Message [@BotFather](https://t.me/BotFather), `/newbot`, copy the token.
2. Message your new bot once — a bot cannot open a conversation with you — then
   open `https://api.telegram.org/bot<TOKEN>/getUpdates` and copy `chat.id`.
3. Put both in `.env` with `TELEGRAM_ENABLED=true`, then
   `docker compose up -d --force-recreate app`.

Off until all three are set; the stack runs fine with no bot at all, and
`POST /api/notify` returns `503 telegram_not_configured` with the vars to set.

### What stops it becoming noise

- **Sent listings are remembered** (`notified_at`, added in `V5`). The shortlist
  is recomputed from the whole table every time, so without this every morning's
  digest would be near-identical to the last — and a notification that repeats
  itself is one you stop opening. Use `?force=true` to re-send deliberately.
- **A score floor** (`TELEGRAM_MIN_SCORE`, default 60) and **a hard cap**
  (`TELEGRAM_MAX_JOBS`, default 8). Telegram's own limit is 4096 characters, so
  the formatter also truncates and says how many it left out.
- **Marked sent only after the send succeeds.** Marking first would silently
  drop those jobs from every future digest the moment a send failed.
- **A failed notification never fails the fetch.** Fetching is the valuable half;
  notifying is convenience on top.

### A note on the explanations in the digest

The digest reuses the Phase 5b explanation tiers. With the local 3B model the
prose reads better but is sometimes **wrong about the reasoning** — in testing it
described the top-ranked job as ranking "lower than expected" and cited a
dimension that had explicitly dropped out. The templated tier is duller and
accurate. Since a digest is read at a glance and acted on, accuracy matters more
here than style, which is why `EXPLANATION_PROVIDER=none` is the default.

## Application tracking (Phase 7)

Records which listings you have acted on, and what happened next.

| Method | Path | Behaviour |
|---|---|---|
| `POST` | `/api/applications` | Start tracking a job. `{"jobId": 172}` — defaults to `APPLIED` |
| `GET` | `/api/applications?status=` | All applications, newest activity first |
| `GET` | `/api/applications/funnel` | Counts per status, every status present even at zero |
| `GET` | `/api/applications/{id}` | One application |
| `PATCH` | `/api/applications/{id}` | Partial update — omitted fields are left alone |
| `DELETE` | `/api/applications/{id}` | Stop tracking |

Statuses run `SAVED → APPLIED → SCREENING → INTERVIEW → OFFER`, with `REJECTED`
and `WITHDRAWN` as terminal states. `SAVED` is the shortlist-for-later case and
is the only one that does not count as having applied.

```bash
curl -X POST localhost:8080/api/applications   -H "Content-Type: application/json"   -d '{"jobId": 172, "notes": "referred by a colleague"}'

curl -X PATCH localhost:8080/api/applications/1   -H "Content-Type: application/json"   -d '{"status": "INTERVIEW", "notes": "call on Tuesday"}'
```

### Why it is not just CRUD

Tracking a job feeds back into the rest of the pipeline, which is the point:

- **The digest stops announcing it.** A job you have applied to is not a
  decision waiting to be made, so re-sending it is noise.
- **`/api/matches` shows where it got to**, in `applicationStatus`, so the
  shortlist stays a list of things still to decide rather than one you have to
  mentally filter.
- **The score is untouched.** Status is attached *after* ranking — applying to a
  job does not make it a better or worse match, and the engine stays pure.

Two rules worth knowing:

- **One application per listing.** Applying twice to the same posting is a
  mistake rather than a case to model, so a second attempt returns `409` naming
  the existing application.
- **`appliedAt` is stamped once and never rewritten.** Moving `APPLIED →
  INTERVIEW` must not reset the date you applied, or "how long have they had
  this?" becomes unanswerable — which is most of what a tracker is for.

## Testing

Tests run in a container like everything else — no host JDK needed. The `test`
service uses the Dockerfile's *build* stage (the runtime stage is a JRE with only
`app.jar` in it, so Gradle is not there) and is behind a profile, so it never
starts with `docker compose up`:

```bash
docker compose --profile test run --rm test
```

That runs the whole suite. It brings up `db` first because the `@SpringBootTest`
context-load test needs a real database to start against — the rule-engine tests
themselves need nothing, and can be run alone if the database is inconvenient:

```bash
docker compose --profile test run --rm test ./gradlew --no-daemon test --tests 'com.jobdiscovery.scoring.*'
```

For an end-to-end check against a running stack — health, migrations, the fetch
and its de-duplication, the profile lifecycle, and the ranked shortlist:

```powershell
.\scripts\smoke-test.ps1            # add -SkipFetch to spare your API quota
```

The smoke test backs up the candidate profile before its destructive checks and
restores it afterwards.

## Limitations (read this)

- **Aggregator APIs do not cover every company or portal.** Postings that are
  LinkedIn-exclusive, or live only on a company's own careers page, never appear
  here.
- **Every stored posting is a preview, not the posting.** Adzuna caps its
  description at 500 characters and Jooble returns a `snippet`. The scoring
  engine compensates — an unmentioned skill is treated as unknown rather than
  absent — but it is working from partial text, and no amount of ranking fixes
  that.
- **The daily cron assumes a host that stays up.** On a laptop it rarely fires,
  which is why there is a catch-up fetch on startup. Genuine daily automation
  needs somewhere always-on; that is not solved here.
- **The local 3B model writes clunky prose and occasionally misreads its own
  evidence.** The templated explainer is duller and accurate, and is the default
  for that reason.
- **Single user, no auth.** The API is unauthenticated and assumes one profile.
  It is bound to localhost for that reason.
- This is a **discovery and triage tool, not a replacement for a job search.**
  It surfaces and ranks candidates for a human to act on.

## Roadmap (intentionally not built)


Web dashboard · semantic search / embeddings · company intelligence (funding,
size, ratings) · resume tailoring & cover-letter generation · Redis caching ·
Resilience4j · Prometheus / Grafana · Kafka · Kubernetes · CI/CD (GitHub
Actions) · multi-user support.

## Screenshots

Four worth capturing, all from a running stack:

| # | URL | What to show |
|---|---|---|
| 1 | `localhost:8080/swagger-ui/index.html` | The six tag groups, one expanded |
| 2 | `localhost:8080/api/matches?limit=3&explain=true` | A ranked result with its breakdown and explanation |
| 3 | `localhost:8081` (Adminer) | `job_listing` with real rows — server `db` |
| 4 | Telegram | A delivered digest, once a bot is set up |

Save them under `docs/` and link them here. They are not committed yet: the
project has only ever run locally, and a screenshot of someone else's data would
be worse than none.
