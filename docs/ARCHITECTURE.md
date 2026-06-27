# Architecture

## Guiding principles

1. **Layered, but feature-first.** We group by *feature* (training, gym, user),
   and inside each feature we keep the classic layers (web → service → data).
   This scales better than one giant `controllers/`, `services/`, `repos/` tree
   and keeps related code together.
2. **Dependencies point inward.** Web depends on service; service depends on
   repository/domain. Never the reverse. The domain knows nothing about HTTP.
3. **The boundary is DTOs.** Controllers speak DTOs (Java records). Entities
   never cross the controller boundary — this prevents accidental lazy-loading
   serialization, over-exposure of fields, and tight coupling of API to schema.
4. **Stateless API.** JWT-based auth; no server-side session. Scales horizontally.
5. **Schema is owned by Flyway**, not Hibernate. `ddl-auto=validate` everywhere.

## Layers

```
HTTP request
   │
   ▼
┌─────────────────┐  @RestController — thin. Maps HTTP ↔ DTO, validates input,
│   Web layer     │  delegates to service, returns ResponseEntity + status code.
└─────────────────┘  No business logic here.
   │ DTOs
   ▼
┌─────────────────┐  @Service — business rules, orchestration, @Transactional
│  Service layer  │  boundaries. Works with entities + domain logic. The "brain".
└─────────────────┘
   │ entities
   ▼
┌─────────────────┐  Spring Data JPA repositories (interfaces). Query methods,
│   Data layer    │  @Query, specifications, projections. No business logic.
└─────────────────┘
   │ SQL (Hibernate)
   ▼
PostgreSQL  ← schema managed by Flyway migrations
```

## Package layout

```
com.cavale
├── CavaleApplication.java
│
├── user/
│   ├── web/         UserController, AuthController
│   ├── service/     UserService, AuthService
│   ├── domain/      User, Role  (JPA entities + enums)
│   ├── repository/  UserRepository
│   └── dto/         RegisterRequest, LoginRequest, AuthResponse, UserResponse
│
├── training/        (running plans, sessions, races/objectives, calendar)
│   ├── web/ service/ domain/ repository/ dto/
│
├── gym/             (templates, exercises, alternatives, live workout logs)
│   ├── web/ service/ domain/ repository/ dto/
│
├── theory/          (exercise how-to guides)
│   ├── web/ service/ domain/ repository/ dto/
│
├── integration/
│   └── strava/      OAuth client, token store, activity sync, scheduler
│
└── common/
    ├── config/      app-wide @Configuration (CORS, OpenAPI, clock, etc.)
    ├── security/    SecurityConfig, JWT filter/encoder/decoder, UserDetails
    ├── exception/   GlobalExceptionHandler (@RestControllerAdvice), error DTO
    └── web/         shared response wrappers, pagination helpers
```

## Cross-cutting concerns

- **Validation** — Jakarta Bean Validation on request records (`@Valid`).
  Constraint violations → 400 via the global handler.
- **Error handling** — one `@RestControllerAdvice` returns a consistent error
  body (`type`, `title`, `status`, `detail`, `field errors`). Consider RFC 9457
  `ProblemDetail` (built into Spring 6+).
- **Transactions** — `@Transactional` at the **service** layer. Reads can be
  `readOnly = true`. Understand proxy semantics (no self-invocation).
- **Security** — method-level (`@PreAuthorize`) plus URL rules in
  `SecurityFilterChain`. Every query that returns user-owned data must be
  scoped to the authenticated user.
- **Configuration & profiles** — `application.yml` + `application-{dev,test,prod}.yml`.
  Secrets via env vars, never committed.
- **Observability** — Actuator (`/actuator/health`, `/info`, `/metrics`),
  Micrometer. Lock down actuator endpoints in prod.

## Why this is "cert-shaped"

Each layer maps to exam domains: the container & DI (services/config), AOP &
proxies (transactions, security, `@Transactional`), Spring Data (repositories),
Spring MVC/REST (web layer), Spring Security (common/security), Boot
auto-config & Actuator (config/observability), and testing slices for each.
See [ROADMAP.md](ROADMAP.md) for the topic mapping.
