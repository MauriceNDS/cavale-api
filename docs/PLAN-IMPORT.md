# Reference plan — mapping the owner's real training plan to the domain

> **Note:** the *application's* import format is NOT this spreadsheet's shape.
> The canonical CSV import format is specified in [IMPORT-FORMAT.md](IMPORT-FORMAT.md);
> this sheet was converted to it by a one-off script in `../plan-data/`
> (gitignored). This document remains as domain-modeling reference.

The owner's actual plan ("Plan SaintéLyon 80 km 2026", 21 weeks, Google
Sheets) is the reference dataset the domain model must be able to represent.
A local copy lives at `../plan-data/` (meta-repo root, **gitignored — personal
data, never commit**).

## Sheet → domain mapping

| Sheet | Contents | Domain concept |
|---|---|---|
| Vue d'ensemble | 21 rows: phase, week n°, dates, week type, volume km, D+ m, load (UA), long-run summary, focus | `TrainingPlan` → **`PlanWeek`** (new concept) |
| Séances détaillées | ~6 sessions/week: day, title, detail (warm-up/body/cool-down), zone, duration, D+, RPE | `PlannedSession` |
| Allures & Zones | 7 named zones: pace range, HR range, %LTHR, RPE, feel, usage; recalibrated after LTHR test | **`PaceZone`** (per user, versioned — new concept) |
| Templates Renfo | 7 session templates (RM, FM-A, FM-B, PW, EXC, NEU, Gainage): exercises with alternatives, sets×reps, load, execution cues, rest + periodization table | `GymTemplate` / `TemplateExercise` (+ `Exercise`) |
| Pliométrie (guide) | Beginner guide, 6 progression levels (contacts/level), golden rules, mistakes | `TheoryGuide` (+ plyo level progression) |
| Nutrition & Course | Gut training 40→80 g/h, hydration, caffeine, pacing plan | later: `RaceNutritionPlan` or content pages |
| Gestion des aléas | Missed session / pain / illness / overload playbook | content pages (theory section) |
| Lisez-moi, Sources | Context, method, references | not imported (docs) |

## Domain model refinements this implies

1. **`PlanWeek`** sits between `TrainingPlan` and `PlannedSession`:
   `weekNumber`, `startDate`, `phase` (label), `weekType`
   (RECOVERY | TRANSITION | BUILD | DELOAD | SHOCK | TAPER | RACE),
   `targetVolumeKm`, `targetElevationM`, `targetLoadUa`, `focus` (text).
2. **`PlannedSession`** gains: `dayOfWeek`, structured `detail`
   (warm-up / body / cool-down text blocks), `zone` (ref to PaceZone name or
   enum), `durationMin` (can be composite "45′ + 55′"—store minutes total +
   raw label), `elevationM`, `rpe` (range, e.g. "2-3" → store min/max),
   optional link to a `GymTemplate` (e.g. "EF 45′ + Renfo FM-A" = run session
   + gym session same day → model as TWO sessions on the same date).
3. **`PaceZone`**: per user, name, paceMin/paceMax (sec/km), hrMin/hrMax,
   pctLthrMin/Max, rpeMin/Max, description, usage. Zones are **versioned/
   recalibratable** (LTHR test in S4 rewrites them; plan says re-test ~S12).
4. **Gym**: sheet's "Alternatives" column confirms per-exercise alternatives;
   templates have a **periodization** dimension (which weeks, which day,
   maintenance vs development, deload −20%) → `PlanWeek` ↔ template
   scheduling handled through `PlannedSession`s.
5. **Load metric**: UA = session RPE × duration (min) (Foster). Computable
   from session fields; weekly target lives on `PlanWeek.targetLoadUa`.

## Import strategy (later phase)

- Seed the owner's real plan via a one-off import (CSV parse → API calls or
  SQL seed) once entities exist. The CSVs in `plan-data/` are the source.
- The AI/MCP flow (Phase 10) generates the same structures conversationally.
