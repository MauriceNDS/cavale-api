# Cavale plan import — canonical CSV format (v1)

A **training plan (= season/cycle)** can be created three ways, all hitting the
same `TrainingPlanService`:

1. **MCP** — Claude creates/adapts plans conversationally (Phase 10 tools).
2. **Manually** — sessions added one by one through the calendar UI/API.
3. **CSV import** — this document. One file, one plan, atomic.

The format below is **owned by Cavale** and versioned. External sources
(Google Sheets, other apps) must be converted to it first — converters live
outside the app; the API only ever accepts the canonical shape.

## Endpoint

`POST /api/plans/import` — `multipart/form-data`, field `file` (UTF-8 CSV).
Creates the plan, its weeks, and its sessions **in one transaction**: any
error (with line number) rolls back everything. Returns
`{planId, weeksCreated, sessionsCreated}`.

## File shape

- Comma-separated, double-quote escaping (RFC 4180), UTF-8, header row required.
- Every row has a `type`: `PLAN`, `WEEK`, or `SESSION`.
- Exactly **one `PLAN` row**, which must come first. `WEEK` rows must precede
  the `SESSION` rows that reference them (by `week_number`).
- Cells not relevant to a row's type are left empty.
- Dates are ISO (`yyyy-MM-dd`). Durations are **integer minutes**. RPE is
  0–10. Any parsing/normalizing of exotic source formats ("1h30", "2-3")
  belongs in converters, never in the API.

## Columns

| Column | PLAN | WEEK | SESSION | Notes |
|---|---|---|---|---|
| `type` | ✓ | ✓ | ✓ | `PLAN` \| `WEEK` \| `SESSION` |
| `name` | ✓ | | | plan name, ≤150 |
| `goal` | opt | | | ≤500 |
| `start_date` | ✓ | ✓ | | plan range start / week Monday |
| `end_date` | ✓ | | | plan range end |
| `week_number` | | ✓ | ✓ | ≥1; SESSION references its week |
| `phase` | | opt | | free label, ≤100 (e.g. "3 · Spécifique II") |
| `week_type` | | ✓ | | `RECOVERY\|TRANSITION\|BUILD\|DELOAD\|SHOCK\|TAPER\|RACE` |
| `target_volume_km` | | opt | | decimal, one fraction digit |
| `target_elevation_m` | | opt | | int |
| `target_load_ua` | | opt | | int (Foster session-RPE × min) |
| `focus` | | opt | | free text |
| `date` | | | ✓ | must fall in the plan range |
| `order_in_day` | | | opt | default 0; two-a-days |
| `discipline` | | | ✓ | `RUN\|GYM\|REST\|CROSS` |
| `title` | | | ✓ | ≤200 |
| `detail` | | | opt | free text (warm-up / body / cool-down) |
| `zone` | | | opt | ≤30 (e.g. `EF`, `Seuil 60`) |
| `duration_min` | | | opt | int minutes |
| `elevation_m` | | | opt | int |
| `rpe_min` / `rpe_max` | | | opt | 0–10, min ≤ max |

## Minimal example

```csv
type,name,goal,start_date,end_date,week_number,phase,week_type,target_volume_km,target_elevation_m,target_load_ua,focus,date,order_in_day,discipline,title,detail,zone,duration_min,elevation_m,rpe_min,rpe_max
PLAN,SaintéLyon 80 km 2026,A: sub-8h30,2026-07-06,2026-11-29,,,,,,,,,,,,,,,,,
WEEK,,,2026-10-05,,14,3 · Spécifique II,SHOCK,88.0,2400,3350,Répétition générale n°1,,,,,,,,,,
SESSION,,,,,14,,,,,,,2026-10-10,0,RUN,SL 4h nocturne,"Nuit, froid, matériel complet, 70 g/h",EF,240,1500,4,5
```

## Versioning

Breaking changes bump the format (v2, …) and the importer will accept a
`format` hint. Additive optional columns are non-breaking.
