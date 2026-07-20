package com.cavale.integration.intervals;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.repository.PlannedSessionRepository;
import com.cavale.training.workout.WorkoutJson;
import com.cavale.training.workout.WorkoutStructure.Allure;
import com.cavale.training.workout.WorkoutStructure.Node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntervalsServiceTest {

    @Mock
    private IntervalsClient client;
    @Mock
    private IntervalsConnectionRepository connectionRepository;
    @Mock
    private PlannedSessionRepository sessionRepository;

    private IntervalsService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new IntervalsService(client, connectionRepository, sessionRepository,
                new IntervalsWorkoutMapper(), new IntervalsProperties(
                        "https://intervals.icu/api/v1", 8));
    }

    @Test
    void connectValidatesTheKeyAndStoresTheConnection() {
        when(client.getAthlete("the-key")).thenReturn(new IntervalsDtos.Athlete("i647048", "Arsène"));
        when(connectionRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IntervalsService.IntervalsStatus status = service.connect(userId, " the-key ");

        assertThat(status.connected()).isTrue();
        assertThat(status.athleteId()).isEqualTo("i647048");
        ArgumentCaptor<IntervalsConnection> saved = ArgumentCaptor.forClass(IntervalsConnection.class);
        verify(connectionRepository).save(saved.capture());
        assertThat(saved.getValue().getApiKey()).isEqualTo("the-key");
    }

    @Test
    void connectRejectsAKeyIntervalsRefuses() {
        when(client.getAthlete(anyString())).thenThrow(new RuntimeException("401"));

        assertThatThrownBy(() -> service.connect(userId, "bad-key"))
                .isInstanceOf(IntervalsException.class)
                .hasMessageContaining("clé API");
    }

    @Test
    void pushSendsOnlyPlannedRunsAsUpsertsKeyedBySessionId() {
        IntervalsConnection connection = new IntervalsConnection(userId, "i647048", "the-key");
        when(connectionRepository.findByUserId(userId)).thenReturn(Optional.of(connection));

        PlannedSession run = session(Discipline.RUN, "Seuil 3×8");
        run.updateWorkoutJson(WorkoutJson.write(List.of(Node.step(Allure.SEUIL60, 480, null))));
        PlannedSession gym = session(Discipline.GYM, "Renfo");
        when(sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(
                eq(userId), any(), any())).thenReturn(List.of(run, gym));

        IntervalsService.PushResult result = service.pushUpcoming(userId);

        assertThat(result.pushed()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IntervalsDtos.EventPayload>> events = ArgumentCaptor.forClass(List.class);
        verify(client).upsertEvents(eq("the-key"), events.capture());
        IntervalsDtos.EventPayload event = events.getValue().getFirst();
        assertThat(event.category()).isEqualTo("WORKOUT");
        assertThat(event.type()).isEqualTo("Run");
        assertThat(event.name()).isEqualTo("Cavale · Seuil 3×8");
        assertThat(event.externalId()).isEqualTo(run.getId().toString());
        assertThat(event.description()).contains("94-100% Pace");
        assertThat(connection.getLastPushAt()).isNotNull();
    }

    @Test
    void unstructuredRunFallsBackToASingleSteadyStep() {
        IntervalsConnection connection = new IntervalsConnection(userId, "i647048", "the-key");
        when(connectionRepository.findByUserId(userId)).thenReturn(Optional.of(connection));

        PlannedSession easyRun = session(Discipline.RUN, "Footing");
        when(sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(
                eq(userId), any(), any())).thenReturn(List.of(easyRun));

        service.pushUpcoming(userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IntervalsDtos.EventPayload>> events = ArgumentCaptor.forClass(List.class);
        verify(client).upsertEvents(anyString(), events.capture());
        assertThat(events.getValue().getFirst().description()).isEqualTo("- Allure EF 65m 65-78% Pace");
    }

    @Test
    void pushWithoutAConnectionFails() {
        when(connectionRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pushUpcoming(userId))
                .isInstanceOf(IntervalsException.class);
    }

    private PlannedSession session(Discipline discipline, String title) {
        TrainingPlan plan = new TrainingPlan(userId, "Plan", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29));
        PlanWeek week = new PlanWeek(plan, 5, LocalDate.of(2026, 8, 3), null,
                WeekType.BUILD, null, null, null, null);
        PlannedSession session = new PlannedSession(week, userId, LocalDate.now().plusDays(2),
                0, discipline, title, null, "EF", 65, 250, null, null);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }
}
