# Cavale API

Backend for **Cavale** — a training companion for ultra-trail running and
the strength work that supports it.

> **Cavale** /ka.val/ — French for *galloping* / *running free*. "Être à la
> cavale" is to run unbound — the feeling this app is built around.

## What it does

- **Calendar & scheduling** — planned trainings by week/month.
- **Running training plans** — structured plans built around races, objectives,
  and holidays. (AI-assisted generation comes later.)
- **Strength training** — reusable session *templates* (e.g. Force Max 1/2,
  Plyometrics + Core, Eccentric), each with a **gym** and **home** variant, and
  exercise **alternatives** when a machine is taken.
- **Live gym tracking** — log weights, reps, and RPE during a session.
- **Exercise theory** — how-to guides (a big help for plyometrics as a beginner).
- **Strava integration** — pull running activities for stats and progression in
  one place. (Free: Strava's API for personal use.)

## Status

🚧 Early development. Backend-first. See [docs/ROADMAP.md](docs/ROADMAP.md).

## Tech

Java 25 · Spring Boot 4 · Maven · PostgreSQL · Spring Security (JWT) ·
Flyway · Testcontainers. Full details in [CLAUDE.md](CLAUDE.md).

## Getting started

> The Spring project is generated as the first roadmap step. Until then:

```bash
docker compose up -d      # Postgres
./mvnw spring-boot:run    # run the API (once generated)
```

## Project layout

| Path                | Purpose                                   |
|---------------------|-------------------------------------------|
| `src/`              | Application source (Spring Boot)          |
| `docs/ROADMAP.md`   | Step-by-step build & learning plan        |
| `docs/ARCHITECTURE.md` | Layering, packaging, conventions       |
| `docs/DOMAIN.md`    | Domain model & entity relationships       |
| `CLAUDE.md`         | Project guide for Claude Code sessions    |

## License

Personal project — all rights reserved for now.
