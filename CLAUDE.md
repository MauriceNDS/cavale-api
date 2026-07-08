# Cavale API — Claude Code Project Guide

Cavale is a training companion for **ultra-trail running** and the
**running-focused strength work** that supports it. This repository is the
**backend API** (a standalone, non-monorepo project). A separate web frontend
will live in a sibling folder later (`cavale-web`).

---

## ⚠️ Working agreement — READ FIRST (updated 2026-07-02)

**Claude writes ALL the code** (backend and frontend). The owner reviews,
uses the app, and asks questions.

- **DO**: implement features end-to-end at a good pace; keep quality high
  (tests, security, best practices) — this is a portfolio piece.
- **DO**: when the owner asks "how/why does X work?", switch to teacher mode:
  explain the mechanism (IoC, proxies, transactions, etc.) clearly, exam-depth
  (the owner still targets a Spring Certified Professional cert).
- **DO**: keep commits clean and conventional; follow the two-step submodule
  flow (commit+push part repo first, then bump the meta repo pointer).
- Explain significant design decisions briefly as they're made (a paragraph,
  not a lecture) so the owner can follow the codebase evolution.

---

## Tech stack (targets — confirm exact patch versions at `start.spring.io`)

| Concern            | Choice                                            |
|--------------------|---------------------------------------------------|
| Language           | **Java 26 (latest, non-LTS)**                                 |
| Framework          | **Spring Boot 4.0.x** (Spring Framework 7)        |
| Build              | **Maven** (wrapper `./mvnw`)                       |
| Database           | **PostgreSQL** (17/18)                             |
| Persistence        | Spring Data JPA / Hibernate                        |
| Migrations         | **Flyway**                                         |
| Security           | Spring Security + **JWT** (stateless)             |
| Validation         | Jakarta Bean Validation                            |
| API docs           | springdoc-openapi (Swagger UI)                     |
| Mapping            | Plain Java / records first; MapStruct if it earns its place |
| Testing            | JUnit 5, Mockito, AssertJ, **Testcontainers**     |
| Dev infra          | Docker Compose (Postgres)                          |
| Observability      | Spring Boot Actuator + Micrometer                  |

**Conventions worth stating up front**
- **Java records** for DTOs (request/response). Entities are plain classes.
- **No Lombok for now** — write boilerplate by hand while learning so the
  mechanics are visible. Revisit later as a deliberate choice.
- **Constructor injection only.** No field `@Autowired`.
- **DTOs at the boundary** — never expose JPA entities directly from controllers.
- **Flyway owns the schema** — `ddl-auto: validate`, never `update` in any env.

## Core domain concept: the plan IS the season

A `TrainingPlan` is a **season/cycle built around an objective** (e.g.
"SaintéLyon 80 km 2026"). Both running AND strength sessions live under it
(PlannedSession.discipline = RUN | GYM | REST | CROSS). One season ends
(COMPLETED/ARCHIVED), the next one is created. Future gym templates and pace
zones also scope to a season.

A plan can be created **three ways, all through the same
`TrainingPlanService`** (never parallel code paths):
1. **MCP** — Claude creates/adapts it conversationally (Phase 10);
2. **manually** — sessions added via the calendar UI/API;
3. **CSV import** — canonical format ONLY (docs/IMPORT-FORMAT.md), atomic,
   line-numbered errors. External sources (Google Sheets…) get converted to
   the canonical format outside the app — the API never learns foreign shapes.

## Architecture

**Feature-first packaging** under `com.cavale`, each feature sliced into
layers internally. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

```
com.cavale
├── user            // auth, accounts, profile
├── training        // running plans, sessions, races/objectives, calendar
├── gym             // templates, exercises, live workout logging
├── theory          // exercise guides / how-to content
├── integration     // Strava (later: Garmin), external sync
└── common          // shared config, security, exception handling, web utils
```

The **domain model** lives in [docs/DOMAIN.md](docs/DOMAIN.md).
The **build/learning plan** lives in [docs/ROADMAP.md](docs/ROADMAP.md).

## Commands (once the project is generated)

```bash
./mvnw spring-boot:run            # run the app
./mvnw test                       # all tests
./mvnw verify                     # tests + integration + checks
./mvnw spotless:apply             # format (if Spotless added)
docker compose up -d              # start Postgres locally
```

## Definition of done (every feature)

- [ ] Flyway migration for any schema change
- [ ] DTOs + Bean Validation on inputs
- [ ] Service holds business logic; controller stays thin
- [ ] Proper HTTP status codes + central exception handling
- [ ] Unit tests (service) + slice/integration tests (web + data)
- [ ] No entity leaked across the API boundary
- [ ] OpenAPI annotations where non-obvious

## AI integration (decided, built in Phase 10)
- Cavale exposes an **MCP server** (Spring AI `spring-ai-starter-mcp-server-webmvc`).
- The owner's Claude subscription (desktop / Claude Code) connects as the MCP
  client and creates/adapts training plans conversationally — **no Anthropic
  API key, no per-token cost**.
- MCP tools live on the **service layer** (same services as REST controllers);
  MCP is a second front door, never duplicate logic. Keep services designed so
  operations are exposable as tools (clear params, DTO in/out, ownership checks).

## Out of scope for now
- Garmin (Strava first; Garmin's dev program needs approval)
- The web frontend (separate project)
