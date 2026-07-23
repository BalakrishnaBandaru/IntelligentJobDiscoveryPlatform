# Project State — Intelligent Job Discovery Platform

> Persistent hand-off doc so a fresh session can resume instantly without
> re-explaining the project. **Claude reads this first at the start of every
> session** and **updates it at the end of every phase or significant change.**
>
> _Last updated: 2026-07-23._

---

## 📍 Current phase

**Phase 3 — Scheduler** — *built & verified; awaiting the user's go-ahead for
Phase 4.*

Daily automated fetch via Spring `@Scheduled` — cron `0 0 6 * * *`, zone
`Asia/Kolkata`, all configurable (`FETCH_SCHEDULE_*` / `FETCH_KEYWORDS` /
`FETCH_LOCATION`). Verified firing under a fast test cron: each run logs
new-jobs-per-run with a per-source breakdown and applies dedup. Next real run is
06:00 IST. User's stated checkpoint is to let it run unattended and check logs.

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
- [ ] **Phase 3 — Scheduler** — built & verified; checkpoint (unattended run) pending
  `@EnableScheduling` + daily cron (06:00 IST, configurable); logs
  new-jobs-per-run. Demonstrated firing under a fast test cron.
- [ ] Phase 4 — Candidate profile
- [ ] Phase 5 — Rule engine + LLM explanations *(most important)*
- [ ] Phase 6 — Telegram notifications
- [ ] Phase 7 — Application tracking
- [ ] Phase 8 — Demo polish (Docker/Adminer already done; Swagger, README, screenshots)

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
  V2 = content_hash + unique index).
- **De-dup by content-hash** of normalised `(title|company|location)`, NOT by
  apply URL (URLs differ across sources / carry volatile tracking params). Unique
  index `ux_job_listing_content_hash`. Catches re-fetches AND cross-source dupes.
- **Per-source location handling** — Jooble uses a configurable fallback location
  (default "India") because it can't geocode Indian cities (see Known issues).
- **Hybrid scoring (Phase 5):** deterministic rule engine produces the score; the
  LLM only *explains* it — never invents its own number.
- **Terminology:** "automation pipeline with AI-assisted scoring", NOT an "AI agent".
- **Target profile:** Java backend, Bangalore / India.

---

## ⚠️ Known issues / flagged, not yet fixed

- **Arbeitnow disabled** (resolved). Verified its API supports no keyword/location
  filtering (only pagination + `visa_sponsorship`). Disabled via
  `arbeitnow.enabled=false`; client code kept + documented for a possible future
  European search. Re-enable with `ARBEITNOW_ENABLED=true`.
- **Jooble can't geocode Indian cities** — `Bangalore`/`Bengaluru`/`Mumbai` → 0
  results; only the country `India` matches. We fall back to `India`, so Jooble
  results are India-wide (Java-relevant, e.g. Mastercard roles), not
  Bangalore-specific. Configurable via `JOOBLE_FALLBACK_LOCATION`.
- **Messy source text** — e.g. Adzuna "Corp" company, quoted titles. Stored as-is.
  Needs a normalisation/cleanup pass **before Phase 5 scoring**.
- **No HTML sanitisation** of `description` (Adzuna/Arbeitnow may contain HTML).
  Revisit before Phase 5/6.

---

## ▶️ Immediate next step (do this when you return)

1. **Await the user's Phase 3 sign-off** — they either let the scheduler run and
   check the 06:00 IST logs, or accept the demonstrated firing.
2. Then start **Phase 4 — Candidate Profile**: a `CandidateProfile` entity
   (skills[], experienceYears, preferredLocations[], preferredCompanies[],
   expectedSalary, noticePeriod, keywords[]); the user supplies real profile data
   via a seed script or simple POST endpoint (no resume parsing). This drives
   Phase 5 scoring — collect the user's REAL values then (profile fields in the
   original brief were placeholders).

Useful endpoints: `POST /api/fetch?keywords=&location=` (all sources),
`POST /api/adzuna/import`, `GET /api/adzuna/search` (raw), `GET /api/jobs[?source=]`,
`GET /api/jobs/count`.

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
never key values._
