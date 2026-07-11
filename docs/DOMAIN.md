# Domain model

A first-pass model of the core concepts. We'll refine entity by entity as we
build — don't treat this as final, treat it as the map.

## Bounded areas

- **User** — accounts, auth, athlete profile.
- **Training (running)** — plans, scheduled sessions, races/objectives, calendar.
- **Gym (strength)** — exercise catalog, session templates, live logging.
- **Theory** — how-to guides attached to exercises.
- **Integration** — imported Strava activities + sync state.

---

## User area

**User** — the athlete and the security principal.
- `id`, `email` (unique), `passwordHash`, `displayName`
- profile: `weightKg`, `heightCm`, `birthDate`, `maxHr`, `restingHr`
- `roles` (e.g. `USER`, `ADMIN`), `createdAt`
- Owns everything below. **All user data is scoped by `userId`.**

---

## Training area (running)

**TrainingPlan** — a block of structured running training.
- `id`, `userId`, `name`, `goal`, `startDate`, `endDate`, `status`
- relates to one or more **Objectives**; contains many **PlannedSessions**.

**Objective** — what the plan is built around. *(implemented)*
- `id`, `planId`, `userId`, `role` (MAIN | SECONDARY), `type` (RACE | RECOVERY | FITNESS | GENERAL)
- `name`, `date?` (event day), `distanceKm?`, `elevationGainM?`, `location?`, `notes?`
- `targetTimeMin?` (the goal), `resultTimeMin?` (once raced)
- Every plan has **exactly one MAIN objective** (created with the plan, DB-enforced
  by a partial unique index, undeletable on its own) — its period IS the plan's
  date range. SECONDARY objectives are intermediate races/milestones the program
  accounts for.
- The **objective page** reads `GET /plans/{id}/progress`: per-week target-vs-actual
  volume/D+/duration (targets from `PlanWeek`, actuals from `Activity`), session
  adherence, current week, countdown.

**PlannedSession** — one scheduled training day (the calendar atom).
- `id`, `planId`, `userId`, `date`, `discipline` (RUN | GYM | REST | CROSS)
- `title`, `description`, `status` (PLANNED | DONE | SKIPPED | MOVED)
- running detail (when discipline = RUN):
  `runType` (EASY | LONG | TEMPO | INTERVALS | HILLS | RECOVERY),
  `targetDistanceKm`, `targetDurationMin`, `targetElevationM`, `targetIntensity`
- when discipline = GYM: links to a **GymTemplate** (the planned template).
- optionally links to a completed **Activity** (Strava) or **WorkoutLog** (gym).

> The **calendar** is just a query over `PlannedSession` by date range + user.

---

## Gym area (strength)

**Exercise** — catalog entry (the "what").
- `id`, `name`, `category` (STRENGTH | PLYOMETRIC | CORE | MOBILITY)
- `primaryMuscles`, `equipment`, `isBodyweight`
- links to a **TheoryGuide** (the "how").

**GymTemplate** — what one session looks like (Force Max 1/2, Plyo+Core, Eccentric).
- `id`, `userId`, `name`, `focus`, `variant` (GYM | HOME)
- ordered list of **TemplateExercise**.
- A logical session can have two variants (GYM/HOME) — model as two templates
  linked by a shared `templateGroupId`, or a `variant` field on one group.

**TemplateExercise** — a slot within a template.
- `id`, `templateId`, `exerciseId`, `order`
- prescription: `sets`, `targetReps`, `targetLoad?`, `tempo?`, `restSec?`
- **alternatives**: ordered list of substitute `exerciseId`s (machine taken).

**WorkoutLog** — a real, performed gym session (live tracking).
- `id`, `userId`, `date`, `templateId?` (what it was based on),
  `plannedSessionId?`, `notes`, `durationMin`
- contains many **SetLog**.

**SetLog** — one performed set (the live-tracking atom).
- `id`, `workoutLogId`, `exerciseId`, `setNumber`
- `weightKg`, `reps`, `rpe?`, `completed`

---

## Theory area

**TheoryGuide** — how to perform an exercise.
- `id`, `exerciseId`, `title`, `body` (markdown), `cues`, `commonMistakes`
- `mediaUrl?` (video/image), `difficulty` (BEGINNER | INTERMEDIATE | ADVANCED)
- especially valuable for plyometrics.

---

## Integration area (Strava)

**StravaConnection** — per-user OAuth link.
- `id`, `userId`, `athleteId`, `accessToken`, `refreshToken`, `expiresAt`
- tokens are **secrets** → encrypt at rest / store carefully.

**Activity** — an imported run (source of truth for actual performance).
- `id`, `userId`, `source` (STRAVA), `externalId` (unique per source)
- `startedAt`, `distanceKm`, `movingTimeSec`, `elevationGainM`
- `avgHr?`, `maxHr?`, `avgPaceSecPerKm`, `type`
- optionally reconciled against a **PlannedSession** (planned vs actual).

> **Statistics & progression** are aggregation queries over `Activity` (running)
> and `SetLog`/`WorkoutLog` (strength) — weekly volume, D+ totals, pace trends,
> est. 1RM progression, etc.

---

## Key relationships (at a glance)

```
User 1──* TrainingPlan 1──* PlannedSession *──1 GymTemplate
TrainingPlan 1──* Objective (1 MAIN + n SECONDARY)  └──? Activity (actual run)
User 1──* GymTemplate 1──* TemplateExercise *──1 Exercise *──? alternatives
User 1──* WorkoutLog 1──* SetLog *──1 Exercise
Exercise 1──1 TheoryGuide
User 1──1 StravaConnection ; User 1──* Activity
```

## Modeling questions to decide as we go

- Gym session GYM/HOME variants: shared group + `variant`, or duplicated templates?
- Planned-vs-actual reconciliation: automatic by date/type, or manual link?
- Per-user exercise catalog vs a shared global catalog with user additions?
- Soft deletes vs hard deletes for plans/logs (history matters for progression).
