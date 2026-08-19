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

> **Status:** built incrementally, phase by phase. This README grows with the
> project and is finalised in Phase 8.

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
| 5 | Rule engine + LLM explanations | ⏳ Next |
| 6 | Telegram notifications | ⬜ |
| 7 | Application tracking | ⬜ |
| 8 | Demo polish (Swagger, README, screenshots) | ⬜ |

## Tech stack

- **Java 21** (LTS)
- **Spring Boot 4.1.x** — Web MVC, Data JPA, Validation, Actuator
- **PostgreSQL 18**
- **Flyway** for DB migrations (introduced in Phase 2)
- **Gradle** (wrapper-pinned)
- **Docker + Docker Compose** — Postgres, Adminer, and the app all run in
  containers; no JDK or Postgres install is required on the host to run it

## Architecture (target)

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
                     |  -> top-N shortlist -> LLM explanation |
                     +---------------------+-----------------+
                                           v
                     +--------------------------------------+
                     |   Filter  ->  Telegram daily digest   |
                     +--------------------------------------+
```

## Running it (Phase 0)

**Prerequisites:** Docker Desktop. No local JDK or Postgres needed to *run* —
everything builds and runs in containers. (A local JDK 21 is only needed if you
want IDE code-completion; the container handles the actual build.)

```bash
# 1. Create your local env file from the template and edit the values
cp .env.example .env          # PowerShell: Copy-Item .env.example .env

# 2. Build and start Postgres + Adminer + the app
docker compose up --build
```

Then verify:

- App health → http://localhost:8080/actuator/health → `{"status":"UP"}`
  (an `UP` status means the app also reached Postgres)
- Adminer (DB browser) → http://localhost:8081
  (System: PostgreSQL, Server: `db`, plus the user/password/db from your `.env`)

Stop everything with `docker compose down` (add `-v` to also wipe the DB volume).

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

## Limitations (read this)

- Aggregator APIs **do not cover every company or portal**. Postings that are
  LinkedIn-exclusive, or that live only on a company's own careers page, will
  not appear here.
- This is a **discovery / triage tool, not a complete replacement** for a job
  search. It surfaces and ranks candidates for a human to act on.

## Roadmap (intentionally not built)

Web dashboard · semantic search / embeddings · company intelligence (funding,
size, ratings) · resume tailoring & cover-letter generation · Redis caching ·
Resilience4j · Prometheus / Grafana · Kafka · Kubernetes · CI/CD (GitHub
Actions) · multi-user support.

## Screenshots

_Placeholder — real screenshots will be added in Phase 8._
