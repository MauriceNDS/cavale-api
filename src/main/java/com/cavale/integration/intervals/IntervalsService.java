package com.cavale.integration.intervals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.repository.PlannedSessionRepository;
import com.cavale.training.workout.WorkoutJson;
import com.cavale.training.workout.WorkoutParser;
import com.cavale.training.workout.WorkoutStructure.Node;

@Service
public class IntervalsService {

    private static final Logger log = LoggerFactory.getLogger(IntervalsService.class);

    /** Calendar slot for pushed workouts — the hour is cosmetic on the watch. */
    private static final String START_TIME = "T07:00:00";

    private final IntervalsClient client;
    private final IntervalsConnectionRepository connectionRepository;
    private final PlannedSessionRepository sessionRepository;
    private final IntervalsWorkoutMapper mapper;
    private final IntervalsProperties properties;

    public IntervalsService(IntervalsClient client,
                            IntervalsConnectionRepository connectionRepository,
                            PlannedSessionRepository sessionRepository,
                            IntervalsWorkoutMapper mapper,
                            IntervalsProperties properties) {
        this.client = client;
        this.connectionRepository = connectionRepository;
        this.sessionRepository = sessionRepository;
        this.mapper = mapper;
        this.properties = properties;
    }

    public record IntervalsStatus(boolean connected, String athleteId, String athleteName,
                                  Instant lastPushAt) {
    }

    public record PushResult(int pushed) {
    }

    @Transactional(readOnly = true)
    public IntervalsStatus status(UUID userId) {
        return connectionRepository.findByUserId(userId)
                .map(c -> new IntervalsStatus(true, c.getAthleteId(), null, c.getLastPushAt()))
                .orElseGet(() -> new IntervalsStatus(false, null, null, null));
    }

    /** Validates the key against the live API before anything is stored. */
    @Transactional
    public IntervalsStatus connect(UUID userId, String apiKey) {
        IntervalsDtos.Athlete athlete;
        try {
            athlete = client.getAthlete(apiKey.trim());
        } catch (Exception e) {
            throw new IntervalsException(
                    "Intervals.icu a refusé cette clé API — vérifiez-la dans Settings → Developer.", e);
        }
        if (athlete == null || athlete.id() == null) {
            throw new IntervalsException("Réponse inattendue d'Intervals.icu — réessayez.");
        }
        IntervalsConnection connection = connectionRepository.findByUserId(userId)
                .map(existing -> {
                    existing.updateKey(athlete.id(), apiKey.trim());
                    return existing;
                })
                .orElseGet(() -> connectionRepository.save(
                        new IntervalsConnection(userId, athlete.id(), apiKey.trim())));
        return new IntervalsStatus(true, connection.getAthleteId(), athlete.name(),
                connection.getLastPushAt());
    }

    @Transactional
    public void disconnect(UUID userId) {
        connectionRepository.findByUserId(userId).ifPresent(connectionRepository::delete);
    }

    /**
     * Publishes one RUN session to the Intervals.icu calendar — the
     * "Export → Garmin Connect" action. external_id = session id, so
     * re-exporting after an edit updates the event (and the watch) in place.
     */
    @Transactional
    public PushResult pushSession(UUID userId, UUID sessionId) {
        IntervalsConnection connection = connectionRepository.findByUserId(userId)
                .orElseThrow(() -> new IntervalsException(
                        "Intervals.icu n'est pas connecté — ajoutez votre clé API dans Paramètres."));
        PlannedSession session = sessionRepository.findById(sessionId)
                .filter(s -> userId.equals(s.getUserId()))
                .orElseThrow(() -> new IntervalsException("Séance introuvable."));
        if (session.getDiscipline() != Discipline.RUN) {
            throw new IntervalsException("Seules les séances de course s'exportent vers la montre.");
        }

        try {
            client.upsertEvents(connection.getApiKey(), List.of(toEvent(session)));
        } catch (Exception e) {
            throw new IntervalsException("L'envoi vers Intervals.icu a échoué — réessayez.", e);
        }
        connection.markPushed(Instant.now());
        log.info("Intervals push: session {} for athlete {}", sessionId, connection.getAthleteId());
        return new PushResult(1);
    }

    /**
     * Publishes the athlete's upcoming planned RUN sessions (today →
     * push-window) to the Intervals.icu calendar. external_id = session id,
     * so a re-push after a plan adaptation updates events in place.
     */
    @Transactional
    public PushResult pushUpcoming(UUID userId) {
        IntervalsConnection connection = connectionRepository.findByUserId(userId)
                .orElseThrow(() -> new IntervalsException("Intervals.icu n'est pas connecté."));

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(properties.pushWindowDays());
        List<IntervalsDtos.EventPayload> events = sessionRepository
                .findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(userId, from, to).stream()
                .filter(s -> s.getDiscipline() == Discipline.RUN)
                .filter(s -> s.getStatus().isPending())
                .map(this::toEvent)
                .toList();
        if (events.isEmpty()) {
            return new PushResult(0);
        }

        try {
            client.upsertEvents(connection.getApiKey(), events);
        } catch (Exception e) {
            throw new IntervalsException("L'envoi vers Intervals.icu a échoué — réessayez.", e);
        }
        connection.markPushed(Instant.now());
        log.info("Intervals push: {} workout(s) for athlete {}", events.size(), connection.getAthleteId());
        return new PushResult(events.size());
    }

    private IntervalsDtos.EventPayload toEvent(PlannedSession session) {
        List<Node> nodes = WorkoutJson.read(session.getWorkoutJson());
        if (nodes.isEmpty() && session.getDurationMin() != null) {
            // Unstructured run ("1h EF") — a single steady step still guides the watch.
            nodes = List.of(Node.step(WorkoutParser.allureOfZone(session.getZone()),
                    session.getDurationMin() * 60, null));
        }
        return IntervalsDtos.EventPayload.workout(
                session.getDate() + START_TIME,
                "Run",
                "Cavale · " + session.getTitle(),
                mapper.describe(nodes),
                session.getId().toString());
    }
}
