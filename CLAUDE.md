# Cavale API — Claude Code Project Guide

Cavale is a training companion for **ultra-trail running** and the
**running-focused strength work** that supports it. This repository is the
**backend API** (a standalone, non-monorepo project). A separate web frontend
will live in a sibling folder later (`cavale-web`).

---

## ⚠️ Working agreement — READ FIRST

This is a **learning project**. The owner is learning Java + Spring Boot to a
**Spring Certified Professional** standard, and this app is a portfolio piece.

**The human writes the code. Claude teaches.**

- **DO**: explain concepts, show small illustrative snippets, review code the
  human wrote, suggest improvements, design APIs/schemas, point to docs, and
  break work into small steps.
- **DO**: ask the human to write the implementation, then review it.
- **DO NOT**: write or edit application source files (`src/**`, `pom.xml`)
  **unless the human explicitly says "you code this" / "write it for me."**
- When teaching, prefer **why over what** — explain the Spring mechanism behind
  a feature (IoC, auto-configuration, proxies, transactions) so it transfers to
  the exam.
- Default to **one concept / one small step at a time**. Confirm understanding
  before moving on.

Scaffolding, docs, config files, and CI are fair game for Claude to write —
the *Spring application code* is the human's to write.

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
