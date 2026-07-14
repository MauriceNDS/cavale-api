package com.cavale.gym.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.GymTemplateVariant;
import com.cavale.gym.domain.SetLog;
import com.cavale.gym.domain.TemplateExercise;
import com.cavale.gym.domain.WorkoutLog;
import com.cavale.gym.domain.WorkoutStatus;
import com.cavale.gym.dto.ExerciseResponse;
import com.cavale.gym.dto.WorkoutDtos.FinishWorkoutRequest;
import com.cavale.gym.dto.WorkoutDtos.LogSetRequest;
import com.cavale.gym.dto.WorkoutDtos.SetLogResponse;
import com.cavale.gym.dto.WorkoutDtos.StartWorkoutRequest;
import com.cavale.gym.dto.WorkoutDtos.WorkoutBlockResponse;
import com.cavale.gym.dto.WorkoutDtos.WorkoutDetailResponse;
import com.cavale.gym.dto.WorkoutDtos.WorkoutLogResponse;
import com.cavale.gym.repository.SetLogRepository;
import com.cavale.gym.repository.TemplateExerciseRepository;
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
    private final PlannedSessionRepository sessionRepository;
    private final GymTemplateService templateService;
    private final ExerciseService exerciseService;

    public WorkoutService(WorkoutLogRepository workoutLogRepository,
                          SetLogRepository setLogRepository,
                          TemplateExerciseRepository templateExerciseRepository,
                          PlannedSessionRepository sessionRepository,
                          GymTemplateService templateService,
                          ExerciseService exerciseService) {
        this.workoutLogRepository = workoutLogRepository;
        this.setLogRepository = setLogRepository;
        this.templateExerciseRepository = templateExerciseRepository;
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
        WorkoutLog log = getOwned(userId, workoutLogId);
        if (log.getStatus() != WorkoutStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Cet entraînement est terminé");
        }
        if (request.reps() == null && request.seconds() == null) {
            throw new IllegalArgumentException("Une série enregistre des reps ou des secondes");
        }
        Exercise exercise = exerciseService.getOwned(userId, request.exerciseId());
        SetLog set = setLogRepository
                .findByWorkoutLogIdAndExerciseIdAndSetNumber(workoutLogId, exercise.getId(),
                        request.setNumber())
                .map(existing -> {
                    existing.updateMeasures(request.reps(), request.weightKg(), request.seconds(),
                            request.position());
                    return existing;
                })
                .orElseGet(() -> setLogRepository.save(new SetLog(log, exercise, request.position(),
                        request.setNumber(), request.reps(), request.weightKg(), request.seconds())));
        return SetLogResponse.from(set);
    }

    @Transactional
    public void deleteSet(UUID userId, UUID setLogId) {
        SetLog set = setLogRepository.findById(setLogId)
                .filter(s -> s.getWorkoutLog().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Set", setLogId));
        setLogRepository.delete(set);
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
        List<WorkoutBlockResponse> blocks = log.getTemplateVariant() != null
                ? templateExerciseRepository
                        .findByVariantIdOrderByPositionAsc(log.getTemplateVariant().getId()).stream()
                        .map(te -> block(log.getUserId(), te))
                        .toList()
                : List.of();
        return new WorkoutDetailResponse(WorkoutLogResponse.from(log, setResponses(log.getId())), blocks);
    }

    private WorkoutBlockResponse block(UUID userId, TemplateExercise te) {
        Exercise exercise = te.getExercise();
        List<SetLogResponse> lastSets = setLogRepository
                .findLastWorkoutSets(userId, exercise.getId()).stream()
                .map(SetLogResponse::from)
                .toList();
        var record = te.getReps() != null
                ? setLogRepository.findRecordWeight(userId, exercise.getId(), te.getReps()).orElse(null)
                : null;
        List<ExerciseResponse> alternatives = templateService.getAlternatives(te.getId()).stream()
                .map(alt -> ExerciseResponse.from(alt.getExercise()))
                .toList();
        return new WorkoutBlockResponse(te.getId(), ExerciseResponse.from(exercise), alternatives,
                te.getSets(), te.getReps(), te.getSeconds(), te.getRestSec(), te.getIntensityPct(),
                te.getNote(), lastSets, record);
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
}
