# Project State — Intelligent Job Discovery Platform

> Persistent hand-off doc so a fresh session can resume instantly without
> re-explaining the project. **Claude reads this first at the start of every
> session** and **updates it at the end of every phase or significant change.**
>
> _Last updated: 2026-07-23._

---

## 📍 Current phase

**Phase 2 — Add More Sources + Deduplication** — *IN PROGRESS.*

Design decided and the source APIs verified against live endpoints. Building the
Jooble + Arbeitnow clients, the content-hash de-duplication, and the `V2`
migration. **Blocked on:** the user pasting the Jooble API key into `.env`
before the full 3-source fetch can be run and verified.

---

## ✅ Phases completed

- [x] **Phase 0 — Setup / Docker scaffold** — **DONE (2026-07-22).**
  Verified: `docker compose up --build` brings up Postgres 18 + Adminer + the
  Spring Boot app; `GET /actuator/health` = `UP` with DB connected. Fully
  containerised — no host JDK/Postgres install.
- [x] **Phase 1 — Fetch jobs (Adzuna)** — **DONE (2026-07-22).**
  Verified: real Adzuna fetch → map → persist works. `max_days_old=30` +
  `sort_by=date` recency fix applied and confirmed (all results within the last
  few days). Manual click-through of applyUrls passed — real, live postings
  (Oracle, Adobe, Deloitte, Kyndryl, SIXT…).
- [ ] **Phase 2 — Add More Sources + Deduplication** — IN PROGRESS
- [ ] Phase 3 — Scheduler
- [ ] Phase 4 — Candidate profile
- [ ] Phase 5 — Rule engine + LLM explanations *(most important)*
- [ ] Phase 6 — Telegram notifications
- [ ] Phase 7 — Application tracking
- [ ] Phase 8 — Demo polish (Docker/Adminer done early; Swagger, README, screenshots)

---

## 🧭 Key decisions

- **Everything runs in Docker** — no local JDK/Postgres install. Postgres 18 +
  Adminer + app all containerised via Docker Compose. (Docker pulled up from the
  plan's Phase 8 to Phase 0.)
- **Java 21 + Spring Boot 4.1.0** (latest stable; user locked in 4.1 over the
  original "3.x"). Gradle 9.5.1 via wrapper. Build/run entirely in containers.
  - _SB4 gotchas already hit:_ use per-tech starters (`spring-boot-starter-flyway`,
    not bare `flyway-core`); `RestClient.Builder` is not auto-configured — build
    clients from the static `RestClient.builder()`.
- **PostgreSQL, not H2** — production-like from the start.
- **Flyway migrations + `spring.jpa.hibernate.ddl-auto=none`** — schema is
  versioned and explicit. Introduced in Phase 1 (moved up from Phase 2).
- **De-dup by content-hash** of normalised `(title + company + location)`, NOT a
  unique constraint on `applyUrl` — because the same job has different URLs
  across sources, and some Adzuna `/land/` URLs carry volatile tracking params.
  (`V2` migration adds a `content_hash` column + unique index.)
- **Hybrid scoring (Phase 5):** a deterministic rule engine produces the score;
  the LLM only *explains* that score — it does NOT invent its own number.
- **Terminology:** "Intelligent Job Discovery Platform" is an *automation
  pipeline with AI-assisted scoring*, **NOT** an "AI agent." README/comments
  must reflect this.
- **Target profile:** Java backend, Bangalore / India (Adzuna `country: in`;
  default search "java developer" / "bangalore").

---

## ⚠️ Known issues / flagged, not yet fixed

- **Messy source text** in some listings — e.g. Adzuna returned company `"Corp"`
  and quoted titles like `"java developer"`. The mapper stores source data
  as-is (faithful). **Needs a normalisation/cleanup pass before Phase 5 scoring**
  so junk text doesn't skew rule matching or LLM prompts.
- **No HTML sanitisation** of `description` (Adzuna/Arbeitnow descriptions can
  contain HTML). Fine for now; revisit before Phase 5/6.
- **Arbeitnow is a feed, not a search** — it ignores keywords/location and
  returns mostly EU/remote roles. Expect lower relevance for an India Java
  search; may need client-side filtering later.

---

## ▶️ Immediate next step (do this when you return)

Finish the Phase 2 implementation:
1. Confirm `JOOBLE_API_KEY` is set in `.env` (placeholder line already added).
2. Build the **Jooble** client (POST, key in URL path) + **Arbeitnow** client
   (GET, no auth) + their mappers → `JobListing` (source = `JOOBLE` /
   `ARBEITNOW`).
3. Add `JobIngestionService` with content-hash de-dup; add the `V2` migration
   (`content_hash` column + unique index; clears legacy pre-dedup rows).
4. Add `POST /api/fetch` to orchestrate all 3 sources.
5. Rebuild, run a 3-source fetch, and report **per-source counts (fetched /
   saved / duplicates)** + **2–3 sample listings each from Jooble and Arbeitnow**
   for the user to eyeball and spot-check applyUrls.

Then **PAUSE for the Phase 2 checkpoint** — the user will manually spot-check a
few Jooble/Arbeitnow applyUrls (same as Adzuna) before we start Phase 3.

---

## 🔑 API keys / config status

| Service | Status | Notes |
|---|---|---|
| **Adzuna** | ✅ working | App ID + Key in `.env`; live fetch confirmed |
| **Jooble** | ⏳ pending | Placeholder line added to `.env`; **awaiting the user's key value** (needed to verify Phase 2) |
| **Arbeitnow** | ✅ no key needed | Open API; response structure verified |
| **LLM API** (Phase 5) | ❌ not configured | Provider TBD (Spring AI vs direct REST) at Phase 5 |
| **Telegram bot** (Phase 6) | ❌ not created | User will create via BotFather when we reach Phase 6 |

---
_Secrets live only in `.env` (git-ignored). This file records **status only**,
never key values._
