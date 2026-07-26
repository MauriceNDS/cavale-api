package com.cavale.gym.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.GymTemplateVariant;
import com.cavale.gym.domain.SetLog;
import com.cavale.gym.domain.TemplateExercise;
import com.cavale.gym.domain.WorkoutBlockOverride;
import com.cavale.gym.domain.WorkoutExtraBlock;
import com.cavale.gym.domain.WorkoutLog;
import com.cavale.gym.domain.WorkoutStatus;
import com.cavale.gym.dto.ExerciseResponse;
import com.cavale.gym.dto.WorkoutDtos.AddExtraBlockRequest;
import com.cavale.gym.dto.WorkoutDtos.AdjustSetsRequest;
import com.cavale.gym.dto.WorkoutDtos.FinishWorkoutRequest;
import com.cavale.gym.dto.WorkoutDtos.LogSetRequest;
import com.cavale.gym.dto.WorkoutDtos.SetLogResponse;
import com.cavale.gym.dto.WorkoutDtos.StartWorkoutRequest;
import com.cavale.gym.dto.WorkoutDtos.SwapBlockRequest;
import com.cavale.gym.dto.WorkoutDtos.WorkoutBlockResponse;
import com.cavale.gym.dto.WorkoutDtos.WorkoutDetailResponse;
import com.cavale.gym.dto.WorkoutDtos.WorkoutGroupAssignment;
import com.cavale.gym.dto.WorkoutDtos.WorkoutLogResponse;
import com.cavale.gym.repository.SetLogRepository;
import com.cavale.gym.repository.TemplateExerciseRepository;
import com.cavale.gym.repository.WorkoutBlockOverrideRepository;
import com.cavale.gym.repository.WorkoutExtraBlockRepository;
import com.cavale.gym.repository.WorkoutLogRepository;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PerceivedEffort;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.repository.PlannedSessionRepository;

/**
 * The live workout. Every set is persisted the moment it's ticked (upsert —
 * autosave), the log stays IN_PROGRESS across phone locks and crashes, and
 * finishing validates the planned session. The detail read model carries
 * the prescription enriched with "last time" prefills and record weights —
 * assembled inside the transaction (OSIV is off).
 */
@Service
public class WorkoutService {

    private final WorkoutLogRepository workoutLogRepository;
    private final SetLogRepository setLogRepository;
    private final TemplateExerciseRepository templateExerciseRepository;
    private final WorkoutBlockOverrideRepository overrideRepository;
    private final WorkoutExtraBlockRepository extraBlockRepository;
    private final PlannedSessionRepository sessionRepository;
    private final GymTemplateService templateService;
    private final ExerciseService exerciseService;

    public WorkoutService(WorkoutLogRepository workoutLogRepository,
                          SetLogRepository setLogRepository,
                          TemplateExerciseRepository templateExerciseRepository,
                          WorkoutBlockOverrideRepository overrideRepository,
                          WorkoutExtraBlockRepository extraBlockRepository,
                          PlannedSessionRepository sessionRepository,
                          GymTemplateService templateService,
                          ExerciseService exerciseService) {
        this.workoutLogRepository = workoutLogRepository;
        this.setLogRepository = setLogRepository;
        this.templateExerciseRepository = templateExerciseRepository;
        this.overrideRepository = overrideRepository;
        this.extraBlockRepository = extraBlockRepository;
        this.sessionRepository = sessionRepository;
        this.templateService = templateService;
        this.exerciseService = exerciseService;
    }

    /**
     * Start a workout — or resume: one IN_PROGRESS workout at a time, so a
     * second "start" returns the running one instead of forking history.
     */
    @Transactional
    public WorkoutDetailResponse start(UUID userId, StartWorkoutRequest request) {
        Optional<WorkoutLog> active = workoutLogRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(userId, WorkoutStatus.IN_PROGRESS);
        if (active.isPresent()) {
            return detail(active.get());
        }

        PlannedSession session = null;
        GymTemplateVariant variant = null;
        if (request.sessionId() != null) {
            session = sessionRepository.findById(request.sessionId())
                    .filter(s -> s.getUserId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("Session", request.sessionId()));
            if (session.getDiscipline() != Discipline.GYM) {
                throw new IllegalArgumentException("Seule une séance Renfo se démarre en entraînement");
            }
            if (workoutLogRepository.findBySessionId(session.getId()).isPresent()) {
                throw new IllegalArgumentException("Cette séance a déjà un entraînement enregistré");
            }
            variant = session.getTemplateVariant();
        }
        if (request.templateVariantId() != null) {
            variant = templateService.getOwnedVariant(userId, request.templateVariantId());
        }
        if (variant == null) {
            throw new IllegalArgumentException(
                    "Associe un programme (variante) à la séance avant de démarrer");
        }

        String templateName = variant.getTemplate().getName() + " · " + variant.getLabel();
        WorkoutLog log = workoutLogRepository.save(
                new WorkoutLog(userId, session, variant, templateName, Instant.now()));
        return detail(log);
    }

    @Transactional(readOnly = true)
    public Optional<WorkoutDetailResponse> active(UUID userId) {
        return workoutLogRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(userId, WorkoutStatus.IN_PROGRESS)
                .map(this::detail);
    }

    @Transactional(readOnly = true)
    public WorkoutDetailResponse get(UUID userId, UUID workoutLogId) {
        return detail(getOwned(userId, workoutLogId));
    }

    /** Autosave: one call per ticked set, upserted by (workout, exercise, set number). */
    @Transactional
    public SetLogResponse logSet(UUID userId, UUID workoutLogId, LogSetRequest request) {
        WorkoutLog log = getOwnedInProgress(userId, workoutLogId);
        if (request.reps() == null && request.seconds() == null) {
            throw new IllegalArgumentException("Une série enregistre des reps ou des secondes");
        }
        Exercise exercise = exerciseService.getOwned(userId, request.exerciseId());
        boolean warmup = Boolean.TRUE.equals(request.warmup());
        SetLog set = setLogRepository
                .findByWorkoutLogIdAndExerciseIdAndSetNumber(workoutLogId, exercise.getId(),
                        request.setNumber())
                .map(existing -> {
                    existing.updateMeasures(request.reps(), request.weightKg(), request.seconds(),
                            request.position(), warmup);
                    return existing;
                })
                .orElseGet(() -> {
                    SetLog created = setLogRepository.save(new SetLog(log, exercise,
                            request.position(), request.setNumber(), request.reps(),
                            request.weightKg(), request.seconds()));
                    created.markWarmup(warmup);
                    return created;
                });
        // Re-ticking a set to correct it must not silently erase how it felt.
        if (request.rir() != null) {
            set.rateReserve(request.rir());
        }
        return SetLogResponse.from(set);
    }

    /**
     * Answer "how many reps did you have left?" after the fact — the rest
     * countdown is dead time, so that is where the question gets asked.
     */
    @Transactional
    public SetLogResponse rateSet(UUID userId, UUID setLogId, Integer rir) {
        SetLog set = setLogRepository.findById(setLogId)
                .filter(s -> s.getWorkoutLog().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Set", setLogId));
        set.rateReserve(rir);
        return SetLogResponse.from(set);
    }

    @Transactional
    public void deleteSet(UUID userId, UUID setLogId) {
        SetLog set = setLogRepository.findById(setLogId)
                .filter(s -> s.getWorkoutLog().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Set", setLogId));
        setLogRepository.delete(set);
    }

    /* ── Mid-workout deviations: swap to an alternative, skip a block ─── */

    /**
     * Replace a block's exercise for THIS workout: the machine is taken, do
     * something equivalent instead. Any owned, non-archived exercise is
     * accepted — the declared alternatives and the suggester only RANK the
     * choices — except one already used by another block (set logs are keyed
     * by exercise, the two blocks would collide). Picking the prescribed
     * exercise reverts. Sets already ticked stay logged against the exercise
     * that was actually performed.
     */
    @Transactional
    public WorkoutBlockResponse swapBlock(UUID userId, UUID workoutLogId, UUID templateExerciseId,
                                          SwapBlockRequest request) {
        WorkoutLog log = getOwnedInProgress(userId, workoutLogId);
        TemplateExercise te = blockOf(log, templateExerciseId);
        Exercise target = exerciseService.getOwned(userId, request.exerciseId());
        boolean prescribed = te.getExercise().getId().equals(target.getId());
        if (!prescribed) {
            if (target.isArchived()) {
                throw new IllegalArgumentException("Cet exercice est archivé");
            }
            if (takenExerciseIds(log, te.getId()).contains(target.getId())) {
                throw new IllegalArgumentException(
                        "« " + target.getName() + " » fait déjà partie de cet entraînement");
            }
        }
        WorkoutBlockOverride override = overrideOf(log, te);
        override.replaceWith(prescribed ? null : target);
        return block(log, te, saveOrPrune(override));
    }

    /**
     * Pair or unpair blocks on the gym floor — "I'll do these two together
     * today". The whole prescribed list arrives at once, exactly like the
     * template editor's grouping, and the same rule holds: members of a
     * superset must be neighbours. Nothing here touches the program.
     */
    @Transactional
    public WorkoutDetailResponse regroup(UUID userId, UUID workoutLogId,
                                         List<WorkoutGroupAssignment> assignments) {
        WorkoutLog log = getOwnedInProgress(userId, workoutLogId);
        if (log.getTemplateVariant() == null) {
            throw new IllegalArgumentException("Cet entraînement n'a pas de programme à regrouper");
        }
        List<TemplateExercise> prescriptions = templateExerciseRepository
                .findByVariantIdOrderByPositionAsc(log.getTemplateVariant().getId());
        Map<UUID, String> wanted = new LinkedHashMap<>();
        for (WorkoutGroupAssignment assignment : assignments) {
            wanted.put(assignment.templateExerciseId(), blank(assignment.groupKey()));
        }
        if (wanted.size() != prescriptions.size()
                || !prescriptions.stream().map(TemplateExercise::getId).collect(Collectors.toSet())
                        .equals(wanted.keySet())) {
            throw new IllegalArgumentException(
                    "L'assignation doit couvrir exactement les exercices du programme");
        }

        List<String> ordered = prescriptions.stream().map(te -> wanted.get(te.getId())).toList();
        java.util.Set<String> closed = new java.util.LinkedHashSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            String key = ordered.get(i);
            if (key != null && (i == 0 || !key.equals(ordered.get(i - 1))) && !closed.add(key)) {
                throw new IllegalArgumentException(
                        "Les exercices d'un même groupe (« " + key + " ») doivent se suivre");
            }
        }

        for (int i = 0; i < prescriptions.size(); i++) {
            TemplateExercise te = prescriptions.get(i);
            String key = ordered.get(i);
            boolean alone = key != null
                    && (i == 0 || !key.equals(ordered.get(i - 1)))
                    && (i == ordered.size() - 1 || !key.equals(ordered.get(i + 1)));
            String effective = alone ? null : key;
            WorkoutBlockOverride override = overrideOf(log, te);
            // back to exactly what the program says ⇒ stop overriding at all
            if (java.util.Objects.equals(effective, te.getGroupKey())) {
                override.clearGrouping();
            } else {
                override.regroup(effective);
            }
            saveOrPrune(override);
        }
        return detail(log);
    }

    /** No time left — drop this block from THIS workout (undoable, template untouched). */
    @Transactional
    public WorkoutBlockResponse skipBlock(UUID userId, UUID workoutLogId, UUID templateExerciseId) {
        WorkoutLog log = getOwnedInProgress(userId, workoutLogId);
        TemplateExercise te = blockOf(log, templateExerciseId);
        WorkoutBlockOverride override = overrideOf(log, te);
        override.skip();
        return block(log, te, saveOrPrune(override));
    }

    /** Un-skip a block (an active swap on it survives). */
    @Transactional
    public WorkoutBlockResponse restoreBlock(UUID userId, UUID workoutLogId, UUID templateExerciseId) {
        WorkoutLog log = getOwnedInProgress(userId, workoutLogId);
        TemplateExercise te = blockOf(log, templateExerciseId);
        WorkoutBlockOverride override = overrideOf(log, te);
        override.restore();
        return block(log, te, saveOrPrune(override));
    }

    /**
     * Adjust a block's set count for THIS workout — down to 0 (the block stays
     * visible, just empty) or above the prescription. Matching the prescribed
     * count clears the override. Already-logged sets beyond the new count are
     * kept: history stays honest, the UI keeps showing them.
     */
    @Transactional
    public WorkoutBlockResponse adjustBlockSets(UUID userId, UUID workoutLogId,
                                                UUID templateExerciseId, AdjustSetsRequest request) {
        WorkoutLog log = getOwnedInProgress(userId, workoutLogId);
        TemplateExercise te = blockOf(log, templateExerciseId);
        WorkoutBlockOverride override = overrideOf(log, te);
        override.adjustSets(request.sets() == te.getSets() ? null : request.sets());
        return block(log, te, saveOrPrune(override));
    }

    /** Same adjustment for a mid-workout addition — its sets are its own row. */
    @Transactional
    public WorkoutBlockResponse adjustExtraBlockSets(UUID userId, UUID workoutLogId,
                                                     UUID extraBlockId, AdjustSetsRequest request) {
        WorkoutLog log = getOwnedInProgress(userId, workoutLogId);
        WorkoutExtraBlock extra = extraBlockRepository.findById(extraBlockId)
                .filter(b -> b.getWorkoutLog().getId().equals(log.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Block", extraBlockId));
        extra.updateSets(request.sets());
        return extraBlock(log.getUserId(), extra);
    }

    /* ── Mid-workout additions: an exercise on top of the program ─────── */

    /**
     * Add an exercise to THIS workout only ("I had time for calf raises").
     * The template is untouched. One block per exercise: its sets are keyed
     * by (workout, exercise, set number), so a duplicate would collide.
     */
    @Transactional
    public WorkoutBlockResponse addExtraBlock(UUID userId, UUID workoutLogId,
                                              AddExtraBlockRequest request) {
        WorkoutLog log = getOwnedInProgress(userId, workoutLogId);
        Exercise exercise = exerciseService.getOwned(userId, request.exerciseId());
        GymTemplateService.validateEffort(exercise, request.reps(), request.seconds());
        if (takenExerciseIds(log, null).contains(exercise.getId())) {
            throw new IllegalArgumentException("Cet exercice fait déjà partie de l'entraînement");
        }
        int position = (int) extraBlockRepository.countByWorkoutLogId(log.getId());
        WorkoutExtraBlock extra = extraBlockRepository.save(new WorkoutExtraBlock(log, exercise,
                position, request.sets(), request.reps(), request.seconds(), request.restSec(),
                request.note() == null || request.note().isBlank() ? null : request.note().trim()));
        return extraBlock(userId, extra);
    }

    /** Remove a mid-workout addition — its logged sets go with it. */
    @Transactional
    public void removeExtraBlock(UUID userId, UUID workoutLogId, UUID extraBlockId) {
        WorkoutLog log = getOwnedInProgress(userId, workoutLogId);
        WorkoutExtraBlock extra = extraBlockRepository.findById(extraBlockId)
                .filter(b -> b.getWorkoutLog().getId().equals(log.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Block", extraBlockId));
        setLogRepository.deleteAll(
                setLogRepository.findByWorkoutLogIdAndExerciseId(log.getId(),
                        extra.getExercise().getId()));
        extraBlockRepository.delete(extra);
    }

    /** Closing the workout is what validates the planned session (status DONE). */
    @Transactional
    public WorkoutLogResponse finish(UUID userId, UUID workoutLogId, FinishWorkoutRequest request) {
        WorkoutLog log = getOwned(userId, workoutLogId);
        if (log.getStatus() != WorkoutStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Cet entraînement est déjà terminé");
        }
        int elapsed = (int) Math.max(1, Duration.between(log.getStartedAt(), Instant.now()).toMinutes());
        log.finish(request.durationMin() != null ? request.durationMin() : elapsed,
                request.perceivedEffort() != null ? request.perceivedEffort() : PerceivedEffort.COMME_PREVU,
                Boolean.TRUE.equals(request.painFlag()),
                request.comment() == null || request.comment().isBlank() ? null : request.comment().trim());
        if (log.getSession() != null) {
            log.getSession().updateStatus(SessionStatus.DONE);
        }
        return WorkoutLogResponse.from(log, setResponses(log.getId()));
    }

    /** Abandon an IN_PROGRESS workout — nothing happened, nothing kept. */
    @Transactional
    public void abandon(UUID userId, UUID workoutLogId) {
        WorkoutLog log = getOwned(userId, workoutLogId);
        if (log.getStatus() != WorkoutStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Un entraînement terminé fait partie de l'historique");
        }
        workoutLogRepository.delete(log); // sets cascade at the DB level
    }

    /* ── Read model ───────────────────────────────────────────────────── */

    private WorkoutDetailResponse detail(WorkoutLog log) {
        Map<UUID, WorkoutBlockOverride> overrides = overrideRepository
                .findByWorkoutLogId(log.getId()).stream()
                .collect(Collectors.toMap(o -> o.getTemplateExercise().getId(), Function.identity()));
        // the pool and the taken set are shared by every block — one query each
        List<Exercise> pool = exerciseService.list(log.getUserId());
        java.util.Set<UUID> taken = takenExerciseIds(log, null);
        List<TemplateExercise> prescriptions = log.getTemplateVariant() != null
                ? templateExerciseRepository
                        .findByVariantIdOrderByPositionAsc(log.getTemplateVariant().getId())
                : List.of();
        List<WorkoutBlockResponse> blocks = new java.util.ArrayList<>(prescriptions.stream()
                .map(te -> block(log, te, overrides.get(te.getId()), pool, taken))
                .toList());
        extraBlockRepository.findByWorkoutLogIdOrderByPositionAsc(log.getId())
                .forEach(extra -> blocks.add(extraBlock(log.getUserId(), extra)));
        return new WorkoutDetailResponse(WorkoutLogResponse.from(log, setResponses(log.getId())),
                blocks, circuitLoops(prescriptions), circuitRestSec(prescriptions));
    }

    /**
     * The whole variant chained into one group is what used to be a circuit,
     * and it runs for as many rounds as its longest member has sets. Anything
     * else — no group, or several — is not a circuit, so this is null and the
     * blocks are read as plain sets×reps.
     */
    private static Integer circuitLoops(List<TemplateExercise> prescriptions) {
        return singleGroup(prescriptions)
                ? prescriptions.stream().mapToInt(TemplateExercise::getSets).max().orElse(1)
                : null;
    }

    /** Rest after the last member of the round — the old between-loops rest. */
    private static Integer circuitRestSec(List<TemplateExercise> prescriptions) {
        return singleGroup(prescriptions) ? prescriptions.getLast().getRestSec() : null;
    }

    private static boolean singleGroup(List<TemplateExercise> prescriptions) {
        return prescriptions.size() > 1
                && prescriptions.stream().allMatch(te -> te.getGroupKey() != null)
                && prescriptions.stream().map(TemplateExercise::getGroupKey).distinct().count() == 1;
    }

    /** Single-block endpoints re-derive the shared context; detail() passes it in. */
    private WorkoutBlockResponse block(WorkoutLog log, TemplateExercise te, WorkoutBlockOverride override) {
        return block(log, te, override, exerciseService.list(log.getUserId()),
                takenExerciseIds(log, null));
    }

    /** One block, with its override (if any) applied: prefills and record follow the EFFECTIVE exercise. */
    private WorkoutBlockResponse block(WorkoutLog log, TemplateExercise te, WorkoutBlockOverride override,
                                       List<Exercise> pool, java.util.Set<UUID> taken) {
        UUID userId = log.getUserId();
        boolean swapped = override != null && override.getExercise() != null;
        Exercise exercise = swapped ? override.getExercise() : te.getExercise();
        List<SetLogResponse> lastSets = setLogRepository
                .findLastWorkoutSets(userId, exercise.getId()).stream()
                .map(SetLogResponse::from)
                .toList();
        var record = te.getReps() != null
                ? setLogRepository.findRecordWeight(userId, exercise.getId(), te.getReps()).orElse(null)
                : null;
        List<Exercise> declared = templateService.getAlternatives(te.getId()).stream()
                .map(com.cavale.gym.domain.TemplateExerciseAlternative::getExercise)
                .toList();

        // Suggestions target the PRESCRIBED movement; everything already in the
        // workout or already declared is excluded from the ranking.
        java.util.Set<UUID> excluded = new java.util.HashSet<>(taken);
        declared.forEach(alt -> excluded.add(alt.getId()));
        List<ExerciseResponse> suggested = AlternativeSuggester
                .suggest(te.getExercise(), pool, excluded, 4).stream()
                .map(ExerciseResponse::from)
                .toList();

        int prescribedSets = te.getSets();
        int sets = override != null && override.getSets() != null
                ? override.getSets() : prescribedSets;
        // the athlete's own pairing wins over the program's, for this workout only
        String groupKey = override != null
                ? override.effectiveGroupKey(te.getGroupKey()) : te.getGroupKey();
        WeightSuggester.Suggestion proposal = suggestWeight(userId, exercise, te.getIntensityPct(),
                te.getReps(), lastSets);
        return new WorkoutBlockResponse(te.getId(), null, ExerciseResponse.from(exercise),
                swapped ? ExerciseResponse.from(te.getExercise()) : null,
                override != null && override.isSkipped(),
                declared.stream().map(ExerciseResponse::from).toList(), suggested,
                sets, prescribedSets, te.getReps(), te.getSeconds(), te.getRestSec(),
                te.getIntensityPct(), te.getNote(), groupKey,
                proposal.weightKg(), proposal.source(), proposal.basisKg(), lastSets, record);
    }

    /**
     * The load to put in front of the athlete. The 1RM is estimated from
     * recent working sets — only needed when the program speaks in
     * percentages, so it is not queried otherwise.
     */
    private WeightSuggester.Suggestion suggestWeight(UUID userId, Exercise exercise,
                                                     Integer intensityPct, Integer targetReps,
                                                     List<SetLogResponse> lastSets) {
        BigDecimal oneRepMax = null;
        if (intensityPct != null) {
            oneRepMax = setLogRepository
                    .findRecentWorkingSets(userId, exercise.getId(),
                            Instant.now().minus(WeightSuggester.ONE_RM_WINDOW_DAYS, ChronoUnit.DAYS))
                    .stream()
                    .map(OneRepMax::of)
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(null);
        }
        return WeightSuggester.suggest(exercise, intensityPct, targetReps,
                lastSets.stream()
                        .map(s -> new WeightSuggester.PastSet(s.weightKg(), s.reps(), s.warmup()))
                        .toList(),
                oneRepMax);
    }

    /** A mid-workout addition as a block: same prefills, no alternatives to offer. */
    private WorkoutBlockResponse extraBlock(UUID userId, WorkoutExtraBlock extra) {
        Exercise exercise = extra.getExercise();
        List<SetLogResponse> lastSets = setLogRepository
                .findLastWorkoutSets(userId, exercise.getId()).stream()
                .map(SetLogResponse::from)
                .toList();
        var record = extra.getReps() != null
                ? setLogRepository.findRecordWeight(userId, exercise.getId(), extra.getReps()).orElse(null)
                : null;
        WeightSuggester.Suggestion proposal = suggestWeight(userId, exercise, null,
                extra.getReps(), lastSets);
        return new WorkoutBlockResponse(null, extra.getId(), ExerciseResponse.from(exercise), null,
                false, List.of(), List.of(), extra.getSets(), extra.getSets(), extra.getReps(),
                extra.getSeconds(), extra.getRestSec(), null, extra.getNote(), null,
                proposal.weightKg(), proposal.source(), proposal.basisKg(), lastSets, record);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Ids of every exercise the workout already uses — effective template
     * exercises (swaps applied) and extra blocks. {@code exceptTemplateExerciseId}
     * leaves one block out (the one being re-assigned).
     */
    private java.util.Set<UUID> takenExerciseIds(WorkoutLog log, UUID exceptTemplateExerciseId) {
        java.util.Set<UUID> taken = new java.util.HashSet<>();
        if (log.getTemplateVariant() != null) {
            Map<UUID, WorkoutBlockOverride> overrides = overrideRepository
                    .findByWorkoutLogId(log.getId()).stream()
                    .collect(Collectors.toMap(o -> o.getTemplateExercise().getId(), Function.identity()));
            for (TemplateExercise other : templateExerciseRepository
                    .findByVariantIdOrderByPositionAsc(log.getTemplateVariant().getId())) {
                if (other.getId().equals(exceptTemplateExerciseId)) {
                    continue;
                }
                WorkoutBlockOverride override = overrides.get(other.getId());
                taken.add(override != null && override.getExercise() != null
                        ? override.getExercise().getId() : other.getExercise().getId());
            }
        }
        extraBlockRepository.findByWorkoutLogIdOrderByPositionAsc(log.getId())
                .forEach(extra -> taken.add(extra.getExercise().getId()));
        return taken;
    }

    private List<SetLogResponse> setResponses(UUID workoutLogId) {
        return setLogRepository.findByWorkoutLogIdOrderByPositionAscSetNumberAsc(workoutLogId).stream()
                .map(SetLogResponse::from)
                .toList();
    }

    private WorkoutLog getOwned(UUID userId, UUID workoutLogId) {
        return workoutLogRepository.findById(workoutLogId)
                .filter(log -> log.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Workout", workoutLogId));
    }

    private WorkoutLog getOwnedInProgress(UUID userId, UUID workoutLogId) {
        WorkoutLog log = getOwned(userId, workoutLogId);
        if (log.getStatus() != WorkoutStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Cet entraînement est terminé");
        }
        return log;
    }

    /** The template exercise, provided it really is a block of this workout's variant. */
    private TemplateExercise blockOf(WorkoutLog log, UUID templateExerciseId) {
        return templateExerciseRepository.findById(templateExerciseId)
                .filter(te -> log.getTemplateVariant() != null
                        && te.getVariant().getId().equals(log.getTemplateVariant().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Block", templateExerciseId));
    }

    private WorkoutBlockOverride overrideOf(WorkoutLog log, TemplateExercise te) {
        return overrideRepository
                .findByWorkoutLogIdAndTemplateExerciseId(log.getId(), te.getId())
                .orElseGet(() -> new WorkoutBlockOverride(log, te));
    }

    /** Persist a meaningful override, prune a neutral one; null = no override left. */
    private WorkoutBlockOverride saveOrPrune(WorkoutBlockOverride override) {
        if (!override.isNeutral()) {
            return overrideRepository.save(override);
        }
        if (override.getId() != null) {
            overrideRepository.delete(override);
        }
        return null;
    }
}
