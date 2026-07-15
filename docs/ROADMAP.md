# Roadmap — build & learning plan

Incremental, vertical-slice delivery. Each phase ships something runnable and
teaches a cluster of **Spring Certified Professional** topics. You write the
code; Claude teaches and reviews.

Legend: 🎓 = primary cert topics introduced.

---

## Phase 0 — Foundations & environment
*Goal: a running Spring Boot app talking to Postgres, and you understand what
every generated file is for.*

- Generate the project at [start.spring.io](https://start.spring.io): Maven,
  Java 26, Spring Boot 4, deps: **Web, JPA, PostgreSQL Driver, Validation,
  Security, Flyway, Actuator, Testcontainers, Lombok (skip), DevTools**.
- Walk through the generated structure: `pom.xml`, `@SpringBootApplication`,
  the wrapper, `application.yml`.
- `docker-compose.yml` for Postgres; wire datasource config.
- First Flyway migration (`V1__init.sql`) — even if just an empty baseline.
- Run it; hit `/actuator/health`.
- 🎓 *Spring Boot, auto-configuration, starters, the IoC container, DI basics,
  externalized config, profiles.*

## Phase 1 — Domain & persistence (start with User)
*Goal: persist and query the first entity through real repositories.*

- `User` entity, `UserRepository` (Spring Data JPA), Flyway migration.
- Understand JPA mapping, identifiers, `equals/hashCode` for entities.
- Repository query methods + a `@Query`.
- `@DataJpaTest` with Testcontainers Postgres.
- 🎓 *Spring Data JPA, the persistence context, repository abstraction,
  transaction fundamentals.*

## Phase 2 — REST API, DTOs, validation, errors
*Goal: a clean, well-behaved HTTP surface for users.*

- `UserController` with DTO records; map entity ↔ DTO.
- Bean Validation on requests (`@Valid`), proper status codes, `ResponseEntity`.
- Global `@RestControllerAdvice` + `ProblemDetail` error bodies.
- `@WebMvcTest` for the controller (mocked service).
- springdoc-openapi → Swagger UI.
- 🎓 *Spring MVC, REST, content negotiation, validation, exception handling,
  web slice testing.*

## Phase 3 — Security & JWT auth
*Goal: real registration/login and per-user data isolation.*

- `SecurityFilterChain`, `BCryptPasswordEncoder`, `UserDetailsService`.
- Register + login endpoints; issue/validate **JWT** (Spring Security resource
  server / `JwtEncoder`/`JwtDecoder`).
- Stateless config; method security (`@PreAuthorize`); scope queries to the
  authenticated user.
- Security tests (`spring-security-test`, `@WithMockUser`).
- 🎓 *Spring Security architecture, filter chain, authn/authz, method security,
  AOP & proxies behind it.*

## Phase 4 — Testing depth (consolidate)
*Goal: a real testing pyramid you can speak to in an interview.*

- Unit-test a service with Mockito + AssertJ.
- Slice tests (`@WebMvcTest`, `@DataJpaTest`) vs full `@SpringBootTest`.
- Testcontainers integration test end-to-end (HTTP → DB).
- 🎓 *Spring TestContext framework, test slices, context caching, mocking.*

## Phase 5 — Training plans & calendar (running)
*Goal: the core product — plans, sessions, objectives, calendar view.*

- `TrainingPlan`, `Objective`, `PlannedSession` (see DOMAIN.md).
- CRUD + the **calendar query**: sessions by date range for a user
  (week/month), with pagination/sorting.
- Validation of date ranges, ownership checks.
- 🎓 *Richer Spring Data (derived queries, `Pageable`, projections), transaction
  boundaries across aggregates.*

## Phase 6 — Gym templates & exercise catalog + theory
*Goal: strength templates with variants & alternatives, plus how-to content.*

- `Exercise`, `TheoryGuide`, `GymTemplate`, `TemplateExercise` (+ alternatives).
- GYM vs HOME variants; ordered exercises; substitute lists.
- 🎓 *More complex JPA mappings (ordered collections, many-to-many, embeddeds).*

## Phase 7 — Live gym tracking
*Goal: record a session set-by-set, fast.*

- `WorkoutLog` + `SetLog`; endpoints tuned for live logging (append a set,
  update reps/weight, finish session).
- Link a log to its planned session / template.
- 🎓 *Write-heavy transactional design, optimistic locking (`@Version`).*

## Phase 8 — Strava integration
*Goal: pull real running data into the app at no extra cost.*

> **Feasibility (verified 2026-07):** Strava's Standard Tier (self-serve, up to
> 10 athletes, 200 req/15 min · 2 000/day) requires the developer to hold an
> active Strava subscription since 2026-06-30 — which the owner already pays,
> so net extra cost = €0. Garmin's Connect Developer Program is business-only
> (personal applications rejected) and currently on hold → Garmin stays out of
> scope; the owner's Garmin data reaches Strava anyway via device sync.
> Auto-validation flow: OAuth connect → sync activities → match to planned
> sessions (date + discipline + duration/distance proximity) → mark DONE and
> link the Activity (planned vs actual).

- OAuth2 **client** flow; store `StravaConnection` tokens securely; refresh.
- Fetch activities with `RestClient`/`WebClient`; map to `Activity`.
- Scheduled sync (`@Scheduled` / `@EnableScheduling`); idempotent upserts by
  `externalId`; respect rate limits.
- 🎓 *Spring Security OAuth2 client, declarative HTTP clients, scheduling,
  resilience.*

## Phase 9 — Statistics & progression
*Goal: the "everything in one place" analytics.*

- Aggregation queries: weekly volume, D+ totals, pace trends (running);
  est. 1RM and load progression (strength).
- Projections / DTO-projecting queries; consider read models.
- 🎓 *Advanced queries, projections, performance (avoid N+1, `@EntityGraph`),
  caching.*

## Phase 10 — MCP server: AI plan creation via Claude subscription
*Goal: the owner's Claude (subscription — desktop app / Claude Code) connects to
Cavale as an MCP client and creates/adapts training plans conversationally.
No Anthropic API key, no per-token cost.*

- **Spring AI MCP server** (`spring-ai-starter-mcp-server-webmvc`): expose
  service-layer operations as MCP tools, e.g. `create_training_plan`,
  `add_planned_session`, `list_objectives`, `get_recent_activities`,
  `get_weekly_load`.
- Tools call the SAME services as the REST controllers — MCP is a second front
  door, never a parallel code path.
- Read tools give Claude context (races, holidays, recent Strava load); write
  tools persist the plan it proposes. Plan revisions happen in conversation
  ("make week 3 easier") via update tools.
- Auth: a personal access token (or long-lived JWT) identifying the owner's
  user account; scoped, revocable. Builds on Phase 3 security.
- 🎓 *(Bonus, beyond cert) Spring AI from the serving side; a very
  portfolio-distinctive integration.*

## Phase 11 — Production hardening & deploy
*Goal: portfolio-ready and runnable on your Proxmox box.*

- Profiles for dev/test/prod; secrets via env; lock down Actuator + CORS.
- Multi-stage `Dockerfile`; `docker-compose` for prod; healthchecks.
- CI (GitHub Actions): build + test + Testcontainers.
- README polish, OpenAPI export, basic load sanity check.
- 🎓 *Boot production features, Actuator, packaging, configuration management.*

---

## Cert-topic coverage map

| Exam area                         | Phases            |
|-----------------------------------|-------------------|
| Container, IoC, DI, beans         | 0, throughout     |
| AOP & proxies                     | 3, 5 (via @Transactional/@PreAuthorize) |
| Properties & profiles             | 0, 11             |
| Spring Boot & auto-config         | 0, 11             |
| Spring Data & transactions        | 1, 5–9            |
| Spring MVC / REST                 | 2, 5–9            |
| Spring Security                   | 3, 8              |
| Testing                           | 1–4, all          |
| Actuator / production             | 0, 11             |

## How we work each phase

1. Claude explains the concept + the Spring mechanism behind it.
2. Claude proposes the smallest next step.
3. **You write the code.**
4. Claude reviews against the Definition of Done in [../CLAUDE.md](../CLAUDE.md).
5. Tests green → move on.

---

## Beyond the core — feature backlog

Phases 0–11 build the platform. The **post-core feature backlog** (readiness &
load metrics, objective model v2, trail GPX race tooling, intelligent MCP
coaching, …) lives in [FEATURE-ROADMAP.md](FEATURE-ROADMAP.md) as fifteen
independently-pickup-able work packages (P1–P15), from the 2026-07 competitive
analysis.
