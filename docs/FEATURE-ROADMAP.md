# Feature roadmap — post-core backlog (P1–P15)

The build/learning plan in [ROADMAP.md](ROADMAP.md) (Phases 0–11) delivers the
**core platform**: plans, calendar, gym, Strava sync, statistics, the MCP
server. **This document is the feature backlog that builds on top of it** —
fifteen discrete, independently-shippable work packages that came out of the
2026-07 competitive analysis against TrainingPeaks, intervals.icu, Runalyze,
Garmin, Strava and Runna.

Each part (`P1`…`P15`) is self-contained: **a future agent can pick up any one
on its own.** Parts are grouped into six phases and ordered so dependencies land
first and independent tracks run in parallel.

> Companion visual roadmap (same content, at-a-glance):
> <https://claude.ai/code/artifact/94712435-d41e-4e5e-982b-13ad8fec37f0>

Legend — effort: **S** ≈ hours–1 day · **M** ≈ 2–4 days · **L** ≈ a week+
(a rough shape, not a commitment; re-scope against live code on pickup).
Layers: **API** · **Web** · **Schema** (Flyway migration) · **MCP** · **Fix**.

---

## Cross-cutting rules (apply to every part)

On top of the API [Definition of done](../CLAUDE.md#definition-of-done-every-feature)
(Flyway migration for schema changes, DTO records + validation, thin controller,
service holds logic, proper status codes, tests, no entity across the boundary):

- **The moat is the context.** Every analytics part ends by surfacing its metric
  in `get_athlete_context` (`AthleteContextService`) — that read model is what
  makes the MCP coach smarter than a Strava-reading wrapper. If a metric isn't in
  the context, the coach can't use it.
- **MCP is a second front door, never a parallel code path.** New MCP tools call
  the same services as REST. Keep operations exposable as tools (clear params,
  DTO in/out, ownership checks).
- **Charts stay hand-rolled SVG.** No charting library — match the existing
  `features/*/charts.tsx` + `lib/useMeasuredWidth.ts` pattern in the web app.
- **Bilingual, always.** All UI strings through i18next FR/EN namespaces;
  MCP-generated content uses the athlete's `preferredLanguage`. Never translate
  domain tokens (EF, VMA, Seuil, D+).
- **Flyway owns the schema.** New tables use UUIDv7 keys and a forward-only
  migration (`V15+`); `ddl-auto: validate` stays on. Records for DTOs,
  constructor injection only.
- **Personal-app scale.** Single athlete, per-user isolation — no multi-tenant or
  coach-roster assumptions.
- **Submodule discipline.** Commit + push inside `api/` (or `web/`) first, then
  bump the meta-repo pointer (see the root README two-step flow).

---

## Phase 1 — Analytics quick wins
*Goal: cheap depth on data already stored (the daily relative-effort series,
records, streams). All four are dependency-free and can run in parallel; each
ends by feeding `get_athlete_context`.*

### P1 · Monotony & Strain (Foster) — S · API · Web · MCP
- **What.** Foster's **monotony** = mean ÷ SD of daily load; **strain** = weekly
  load × monotony, over rolling 7-day windows. Flag monotony **> 2.0** as an
  illness / overtraining early-warning (Runalyze's signature; even Garmin skips
  it). Runs on the daily-effort series `RunningStatsService` already builds.
- **Touches.** `RunningStatsService`, running-stats endpoint, a `/stats` (Course)
  tile, `AthleteContextService`.
- **Depends.** — (ready now).
- **Done when.** 52-week monotony & strain series returned; > 2.0 flagged; tile on
  `/stats`; field added to the context training-load summary.

### P2 · Single "Training Status" verdict — S · API · Web · MCP
- **What.** Fuse the already-computed fitness trend + ACWR zone + form into one
  plain-language label — **Productive / Maintaining / Overreaching / Recovery /
  Detraining**. One glanceable answer over the metric wall.
- **Touches.** `RunningStatsService`, a status pill on Home, `AthleteContextService`.
- **Depends.** — (ready now; optionally reads P1 strain).
- **Done when.** Deterministic label from documented thresholds; pill on Home; in
  context.

### P3 · Per-run injury guardrail (RUNSAFE) — S · API · MCP
- **What.** Compute each run's distance vs the athlete's **trailing-30-day longest
  run** (BJSM 2025, N=5,205): 30–100% longer = elevated (+52% hazard), > 100% =
  high (+128%). Keep ACWR as the chronic view; this guards individual long runs
  and is read by the coach before it prescribes one.
- **Touches.** `AthleteStatsService`, Activity corpus, `AthleteContextService` (a
  guard the coach consults).
- **Depends.** — (ready now).
- **Done when.** Rolling-30-day-max exposed; next long-run risk banded; in context
  for P15 to enforce.

### P4 · Road-only race-time estimates — S · API · Fix
- **What.** *The reported fix.* Road predictions are deflated because trail runs
  with heavy D+ feed them. Restrict the road predictors (Riegel / Cameron /
  Vickers-Vertosick + records) to **road-like efforts** (low D+/km, e.g.
  `< ~15–20 m/km`) or grade-adjust pace first. The trail objective estimates keep
  their hilly-long-run set, unchanged.
- **Touches.** `RunningStatsService` / `AthleteStatsService` prediction methods,
  the run-selection filter.
- **Depends.** — (ready now; informs P5, P8).
- **Done when.** Road predictions use road-like runs only; the D+/km threshold is
  documented; trail estimates untouched.

---

## Phase 2 — Deeper metrics
*Goal: new fitness signals from data already captured (HR/pace relationships,
best-effort splits, long-run streams). Runs alongside Phases 1 and 4.*

### P5 · Effective VO2max trend + critical pace — M · API · Web · MCP
- **What.** Estimate VO2max/VDOT from each road-like run's HR↔pace as a continuous
  fitness trend (à la Runalyze), and fit a **pace-duration (mean-max) curve →
  critical speed** from best-effort splits. Complements the existing
  meters-per-heartbeat efficiency; feeds predictions.
- **Touches.** `RunningStatsService`, `ActivityBestEffort` corpus, two `/stats`
  charts, `AthleteContextService`.
- **Depends.** **P4** recommended (road filter).
- **Done when.** 12-month VO2max trend + critical speed exposed; charts on
  `/stats`; in context.

### P6 · Aerobic durability / late-run fade — M · API · Web · MCP
- **What.** From `streams_json`, compute HR/pace **decoupling** (1st vs 2nd half)
  and fade deep into long runs → a durability score. Extends the existing
  duration-checkpoints — durability is what decides ultra outcomes.
- **Touches.** `RunningStatsService` (streams parsing), the checkpoint code, a
  `/stats` chart, `AthleteContextService`.
- **Depends.** — (ready now).
- **Done when.** Decoupling % per long run + a rolling durability trend; on
  `/stats`; in context.

### P7 · Personal trail performance index — S · API · Web · MCP
- **What.** An ITRA/UTMB-style personal index from best results over 36 months on
  the **km-effort** scale already computed (weighted mean, recent weighted
  higher). One number the athlete watches climb — engagement hook and coaching
  input in one.
- **Touches.** `AthleteStatsService` (km-effort, best races), a Home tile,
  `AthleteContextService`.
- **Depends.** — (ready now; pairs with P12).
- **Done when.** Index from best-N races / 36 mo; weighting documented; Home tile;
  in context.

---

## Phase 3 — Objective model v2 (foundational)
*Goal: the schema the intelligent coach needs. Both parts touch the `objective`
entity (`training` package), its selection UI, and the MCP context — one agent
can land them in a single migration. Unblocks Phase 6.*

### P8 · Objective kind: Road vs Trail — M · Schema · API · Web · MCP
- **What.** Add `kind` (**ROAD | TRAIL**) to `Objective`. Road objectives express
  targets/predictions as **pace ranges** (e.g. `4:20–4:40 /km`); trail objectives
  use **time + km-effort + D+**. The MCP context adapts its target representation
  to the kind.
- **Touches.** Migration (`objective.kind`), `Objective` entity/DTO, the
  objective-selection UI, `AthleteContextService`, MCP `update_objective` /
  `add_secondary_objective`, prediction display.
- **Depends.** — (ready now; foundational).
- **Done when.** Kind persisted + editable; road shows pace ranges, trail shows
  time/km-effort; context carries kind + the right target representation; MCP
  tools accept it.

### P9 · Objective intensity: Balance vs Performance — S · Schema · API · Web · MCP
- **What.** Add `intensity` (**BALANCE | PERFORMANCE**) to `Objective`. *Balance* =
  smoother progression, conservative guardrails. *Performance* = the most
  aggressive ramp that still stays inside the injury guardrails. Consumed by plan
  generation (P13) and guardrail strictness (P15).
- **Touches.** Migration (share with P8), `Objective`, objective UI,
  `AthleteContextService`, MCP tools.
- **Depends.** — (ready now; bundle with P8).
- **Done when.** Intensity persisted + editable; in context; the effect on ramp
  rate + guardrail thresholds is documented for P13/P15.

---

## Phase 4 — Logging & capture
*Goal: capture what the app doesn't yet — the shoes you rotate and the
cross-training you actually do. Independent of everything else; slot in anytime.*

### P10 · Shoe / gear + ask-on-validate + mileage — M · Schema · API · Web · Strava
- **What.** Add a per-athlete **Shoe** entity (name, brand, purpose
  road/trail/race, retired, accumulated km). **When validating an activity
  (manual or Strava-matched), ask which shoe from the athlete's list**; accrue
  mileage; alert at a retirement threshold. Optionally seed the list + per-activity
  gear from Strava (its API exposes gear).
- **Touches.** Migration (`shoe`, `activity.shoe_id`), Shoe CRUD, the validation
  flow (`SessionMatchService` / `validate` + `validate-strava`), a shoe-manager UI
  + a shoe picker in the validation wizard, optional Strava gear import.
- **Depends.** — (ready now).
- **Done when.** Shoes CRUD; validation asks for a shoe; mileage accrues +
  retirement alert; (optional) Strava import.

### P11 · Bicycle cross-training — S · Schema · API · Web
- **What.** A bike session **validatable in one click with time + distance only** —
  no GPS, no streams, no tracking. Reuse the existing `CROSS` discipline (or a BIKE
  subtype). It contributes a **time-based effort** to training load but is excluded
  from run stats and predictions.
- **Touches.** `PlannedSession` / `Activity` (CROSS/BIKE + minimal fields), the
  one-click validate flow, calendar/session UI, the activity-feed label; ensure
  it's excluded from run-only stats.
- **Depends.** — (ready now).
- **Done when.** A bike session can be planned + validated in one click
  (time+distance); appears in the feed; contributes a time-based effort to load;
  excluded from run predictions.

---

## Phase 5 — Trail race tooling
*Goal: the on-brand differentiator the road-first giants structurally can't
follow — because it runs on your personalized km-effort model.*

### P12 · GPX course planner + pacing — L · Schema · API · Web · Chart · MCP
- **What.** Upload a race **GPX** → parse track + build the elevation profile →
  compute **grade-adjusted split times and aid-station arrivals** from the
  athlete's own median sec/km-effort (already computed for the objective
  estimates). Attach the course to a trail objective; expose it to the coach so
  pacing advice is course-specific. A fueling / aid-station layer (carbs-per-hour,
  checklist) is a natural fast-follow.
- **Touches.** Migration (`course` + `waypoint`, linked to `objective`), a new GPX
  parser, a pacing service (reuses the trail sec/km-effort model), a course screen
  with an elevation-profile SVG + split table, an MCP read tool for the coach.
- **Depends.** **P8** recommended (trail objective kind); **P7** optional; the
  trail sec/km-effort model (exists).
- **Done when.** GPX import + profile render; per-segment grade-adjusted splits +
  cumulative arrivals from personal pace; attachable to a trail objective; readable
  by MCP.

---

## Phase 6 — Intelligent MCP coaching (capstone)
*Goal: a coach that turns a one-line objective into a scientifically-sound,
correctly-structured plan — and adapts it safely. Needs the objective model
(Phase 3) and reads every metric from Phases 1–2 through the context.*

### P13 · Scientific plan generation — right "form" + best "context" — L · MCP · API
- **What.** The headline. Let the athlete say *"create a plan for the 6000D next
  year"* and get back a plan with the right **form** (structurally valid — passes
  `WorkoutParser`, one MAIN objective, valid week types/disciplines) and the best
  **context** (periodization base→build→peak→taper, deload every ~4th week,
  polarized distribution, progressive overload against ACWR/form). **Road vs trail
  differ** (paces vs km-effort/vert); **Balance vs Performance** (P9) sets the ramp.
  Add a `scaffold_plan` MCP tool that returns a **validated week skeleton** with
  target loads for the LLM to fill, then validate the generated sessions. Use the
  `plan-data/` CSVs as exemplars; encode the principles in the MCP server
  instructions (`McpConfig`).
- **Touches.** `McpConfig` instructions, `CoachTools` (+ `scaffold_plan` + plan
  validation), `AthleteContextService` (objective-aware), `plan-data/` exemplars.
- **Depends.** **P8**, **P9**; consumes **P1–P6**, **P3** via the context.
- **Done when.** A natural-language objective → a structurally valid, periodized
  plan; road/trail + balance/performance aware; guardrails respected; written in
  the athlete's `preferredLanguage`.

### P14 · Adaptation: forward-rebuild, never shuffle-or-stack — M · MCP · API
- **What.** When sessions are missed, rebuild **forward against the fixed race
  date** instead of stacking. Tiered: 1 miss → ignore; ±1 wk → reschedule;
  3+ / a full week → auto-rebuild; 2+ wk → rebuild-to-date or extend; 4+ →
  restart. Redistribute only **50–75% of missed volume over 3–4 weeks, hard days
  first, and never two hard days back-to-back.**
- **Touches.** `CoachTools` (a rebuild/realign tool + missed-session signals),
  planned-session status logic, MCP instructions.
- **Depends.** **P13** (or parallel).
- **Done when.** The rebuild reshapes upcoming weeks per the tiers + redistribution
  rule; never schedules two consecutive hard days; the change is explained to the
  athlete.

### P15 · Guardrails, explainability & taper — M · MCP
- **What.** Wire the safety + trust norms into the coach: deprioritize hard work
  when `INJURED/SICK` (or readiness low — future), enforce the **RUNSAFE long-run
  cap** (P3) and the ACWR band, let **intensity** (P9) tune aggressiveness,
  **explain every change** in plain language, and apply a textbook **taper**
  (~2 weeks, cut volume 41–60% exponentially, hold intensity + frequency).
- **Touches.** `McpConfig` instructions, `CoachTools` write-guards.
- **Depends.** **P3**, **P9**, **P13**.
- **Done when.** The coach adjusts against the guardrails; every plan change carries
  a rationale; the taper is auto-applied near a race.

---

## Sequencing at a glance

- **Start now, in parallel** (all dependency-free): **P1–P4**, **P5–P7**,
  **P10–P11**.
- **Then land** **P8–P9** (objective model, one migration) to unblock the capstone.
- **P12** (GPX) can go anytime after P8.
- **Finish with** **P13 → P14 / P15**, which read everything the earlier phases
  pushed into `get_athlete_context`.

| Origin (2026-07 analysis) | Part |
|---|---|
| Monotony & Strain (Foster) | P1 |
| Single "Training Status" verdict | P2 |
| Per-run injury guardrail | P3 |
| *Fix:* road-only estimated times | P4 |
| Effective VO2max + critical pace | P5 |
| Aerobic durability / late-run fade | P6 |
| Personal trail performance index | P7 |
| Road vs trail objective (road paces) | P8 |
| Objective intensity balance/performance | P9 |
| Gear / shoe mileage (ask on validate) | P10 |
| Bicycle cross-training | P11 |
| GPX course planner + pacing | P12 |
| Onboarding / goal wizard (MCP plan gen) | P13 |
| Missed-work forward-rebuild | P14 |
| Explain every change + taper | P15 |
