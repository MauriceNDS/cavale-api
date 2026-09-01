package com.cavale.training.workout;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.WeekType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bug this pins: {@code durationMin} and the stored workout structure were
 * two independent numbers, and different screens read different ones — the
 * same session showed 76′ on its card and 80′ in the week ring. Every reader
 * now goes through {@link SessionDuration}, so a session has exactly one
 * prescribed duration.
 */
@ExtendWith(MockitoExtension.class)
class SessionDurationTest {

    private static final UUID USER = UUID.randomUUID();

    /** Real sessions from the SaintéLyon 2026 plan, with the minutes the coach stored. */
    @Test
    void plannedMinutes_comesFromTheStructure_notTheStoredMinutes() {
        // 20′ + 2×(20′ + 3′ récup) + 10′ = 4560 s = 76′, stored as 80
        assertThat(plannedMinutes("20′ EF + 2×20′ Allure course (récup 3′) + 10′ EF", "EF", 80))
                .isEqualTo(76);

        // 20′ + 3 lignes + 8×(45″ + 45″ récup) + 10′ = 2880 s = 48′, stored as 55
        assertThat(plannedMinutes("Échauffement : 20′ EF + 3 lignes droites. Corps : 8 × 45″ en côte "
                + "forte à effort VMA, récup = descente en trot. Retour au calme : 10′ EF.", "VMA", 55))
                .isEqualTo(48);

        // 20′ + 3 lignes + 3×(8′ + 8′ récup) + 10′ = 4920 s = 82′, stored as 78
        assertThat(plannedMinutes("Échauffement : 20′ EF + 3 lignes droites. Corps : 3 × 8′ en montée "
                + "régulière 4-6 % à effort Seuil 60 (FC 159-168), récup = descente en trot. "
                + "Retour au calme : 10′ EF.", "Seuil 60", 78))
                .isEqualTo(82);
    }

    /** A session whose text yields no blocks still has a duration to show. */
    @Test
    void plannedMinutes_fallsBackToStoredMinutesWithoutAStructure() {
        PlannedSession session = run("Récupération libre", "EF", 45);
        assertThat(SessionDuration.plannedMinutes(session)).isEqualTo(45);
        assertThat(SessionDuration.durationDriftSeconds(session)).isNull();
    }

    /** GYM has no workout tree — its stored minutes are all there is. */
    @Test
    void plannedMinutes_usesStoredMinutesForGym() {
        PlannedSession gym = new PlannedSession(week(), USER, LocalDate.of(2026, 9, 16), 0,
                Discipline.GYM, "EXC allégée", "EXC allégée (35′).", null, 45, null, null, 5);
        assertThat(SessionDuration.plannedMinutes(gym)).isEqualTo(45);
        assertThat(SessionDuration.durationDriftSeconds(gym)).isNull();
    }

    /**
     * The invariant the athlete's report asked for: a RUN carrying blocks must
     * agree with its stored minutes to within a minute of rounding.
     */
    @Test
    void durationDrift_isTheGapBetweenTheTwoStoredNumbers() {
        assertThat(drift("20′ EF + 2×20′ Allure course (récup 3′) + 10′ EF", "EF", 80))
                .isEqualTo(240); // stored 80′, blocks 76′
        assertThat(drift("20′ EF + 2×20′ Allure course (récup 3′) + 10′ EF", "EF", 76))
                .isZero();
    }

    /** A long run whose body lives in the title is padded to its stored total. */
    @Test
    void plannedMinutes_matchesStoredMinutesWhenTheParserPadsTheGap() {
        // 30′ of allure course inside a 3h session: the missing 2h30 becomes a
        // leading EF block, so the structure covers the whole prescription
        assertThat(plannedMinutes("30′ Allure course en fin de sortie.", "EF", 180))
                .isEqualTo(180);
        assertThat(drift("30′ Allure course en fin de sortie.", "EF", 180)).isZero();
    }

    private static int plannedMinutes(String detail, String zone, int durationMin) {
        return SessionDuration.plannedMinutes(structured(detail, zone, durationMin));
    }

    private static Integer drift(String detail, String zone, int durationMin) {
        return SessionDuration.durationDriftSeconds(structured(detail, zone, durationMin));
    }

    /** A session stored the way the app stores it: text parsed once, structure persisted. */
    private static PlannedSession structured(String detail, String zone, int durationMin) {
        PlannedSession session = run(detail, zone, durationMin);
        session.updateWorkoutJson(WorkoutJson.write(
                WorkoutParser.parse(detail, zone, durationMin).nodes()));
        return session;
    }

    private static PlannedSession run(String detail, String zone, int durationMin) {
        return new PlannedSession(week(), USER, LocalDate.of(2026, 9, 2), 0, Discipline.RUN,
                "Séance", detail, zone, durationMin, null, null, 6);
    }

    private static PlanWeek week() {
        return new PlanWeek(null, 1, LocalDate.of(2026, 8, 31), "Build", WeekType.BUILD,
                null, null, null, null);
    }
}
