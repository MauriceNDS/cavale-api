package com.cavale.training.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.cavale.TestcontainersConfiguration;
import com.cavale.common.config.JpaAuditingConfig;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.user.domain.User;
import com.cavale.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class PlannedSessionRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrainingPlanRepository planRepository;

    @Autowired
    private PlanWeekRepository weekRepository;

    @Autowired
    private PlannedSessionRepository sessionRepository;

    @Test
    void calendarQuery_returnsOnlyOwnSessionsInRangeOrdered() {
        User alice = userRepository.save(new User("alice@cavale.run", "hash", "Alice"));
        User bob = userRepository.save(new User("bob@cavale.run", "hash", "Bob"));

        TrainingPlan alicePlan = planRepository.save(new TrainingPlan(alice.getId(), "SaintéLyon", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29)));
        TrainingPlan bobPlan = planRepository.save(new TrainingPlan(bob.getId(), "Other", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29)));

        PlanWeek aliceWeek = weekRepository.save(new PlanWeek(alicePlan, 10, LocalDate.of(2026, 9, 7),
                "Spécifique I", WeekType.SHOCK, new BigDecimal("86.0"), 2200, 3250, "1er week-end choc B2B"));
        PlanWeek bobWeek = weekRepository.save(new PlanWeek(bobPlan, 10, LocalDate.of(2026, 9, 7),
                null, WeekType.BUILD, null, null, null, null));

        // Alice: in range (two on the same day, ordered), one out of range
        sessionRepository.save(new PlannedSession(aliceWeek, alice.getId(), LocalDate.of(2026, 9, 12), 1,
                Discipline.RUN, "SL 3h30", null, "EF", 210, 1350, 4, 5));
        sessionRepository.save(new PlannedSession(aliceWeek, alice.getId(), LocalDate.of(2026, 9, 12), 0,
                Discipline.GYM, "Gainage 12'", null, null, 12, null, null, null));
        sessionRepository.save(new PlannedSession(aliceWeek, alice.getId(), LocalDate.of(2026, 9, 20), 0,
                Discipline.RUN, "Hors plage", null, "EF", 60, null, 2, 3));
        // Bob: same dates, must never leak into Alice's calendar
        sessionRepository.save(new PlannedSession(bobWeek, bob.getId(), LocalDate.of(2026, 9, 12), 0,
                Discipline.RUN, "Bob run", null, null, 60, null, null, null));

        List<PlannedSession> calendar = sessionRepository
                .findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(
                        alice.getId(), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13));

        assertThat(calendar).hasSize(2);
        assertThat(calendar.get(0).getTitle()).isEqualTo("Gainage 12'");
        assertThat(calendar.get(1).getTitle()).isEqualTo("SL 3h30");
        assertThat(calendar).allSatisfy(s -> assertThat(s.getUserId()).isEqualTo(alice.getId()));
    }
}
