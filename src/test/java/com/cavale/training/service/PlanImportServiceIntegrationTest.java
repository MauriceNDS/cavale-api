package com.cavale.training.service;

import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.cavale.TestcontainersConfiguration;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.WeekType;
import com.cavale.training.dto.ImportResult;
import com.cavale.training.repository.TrainingPlanRepository;
import com.cavale.user.domain.User;
import com.cavale.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-transaction import tests (no test-level @Transactional so rollback
 * semantics are the production ones).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlanImportServiceIntegrationTest {

    private static final String HEADER = "type,name,goal,start_date,end_date,week_number,phase,week_type,"
            + "target_volume_km,target_elevation_m,target_load_ua,focus,date,order_in_day,discipline,title,"
            + "detail,zone,duration_min,elevation_m,rpe_min,rpe_max\n";

    @Autowired
    private PlanImportService importService;

    @Autowired
    private TrainingPlanService planService;

    @Autowired
    private TrainingPlanRepository planRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void importPlan_createsPlanWeeksAndSessions() {
        User user = userRepository.save(new User("import-ok@cavale.run", "hash", "Importer"));
        String csv = HEADER
                + "PLAN,SaintéLyon 2026,sub-8h30,2026-07-06,2026-11-29,,,,,,,,,,,,,,,,,\n"
                + "WEEK,,,2026-10-05,,14,3 · Spécifique II,SHOCK,88.0,2400,3350,Répétition générale,,,,,,,,,,\n"
                + "SESSION,,,,,14,,,,,,,2026-10-10,0,RUN,SL 4h nocturne,\"Nuit, froid, 70 g/h\",EF,240,1500,4,5\n"
                + "SESSION,,,,,14,,,,,,,2026-10-05,0,GYM,FM-A maintien,,,55,,,\n";

        ImportResult result = importService.importPlan(user.getId(), new StringReader(csv));

        assertThat(result.weeksCreated()).isEqualTo(1);
        assertThat(result.sessionsCreated()).isEqualTo(2);

        var weeks = planService.getWeeks(user.getId(), result.planId());
        assertThat(weeks).hasSize(1);
        assertThat(weeks.getFirst().getWeekType()).isEqualTo(WeekType.SHOCK);

        List<PlannedSession> calendar = planService.getCalendar(user.getId(),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11));
        assertThat(calendar).extracting(PlannedSession::getTitle)
                .containsExactly("FM-A maintien", "SL 4h nocturne");

        planService.deletePlan(user.getId(), result.planId());
    }

    @Test
    void importPlan_rollsBackEverythingOnError() {
        User user = userRepository.save(new User("import-ko@cavale.run", "hash", "Importer"));
        String csv = HEADER
                + "PLAN,Broken plan,,2026-07-06,2026-11-29,,,,,,,,,,,,,,,,,\n"
                + "WEEK,,,2026-10-05,,14,,SHOCK,,,,,,,,,,,,,,\n"
                + "SESSION,,,,,99,,,,,,,2026-10-10,0,RUN,Orphan session,,,,,,\n";

        assertThatThrownBy(() -> importService.importPlan(user.getId(), new StringReader(csv)))
                .isInstanceOf(PlanImportException.class)
                .hasMessageContaining("Line 4")
                .hasMessageContaining("week 99");

        assertThat(planRepository.findByUserIdOrderByStartDateDesc(user.getId()))
                .as("failed import must leave nothing behind")
                .isEmpty();
    }

    @Test
    void importPlan_rejectsSessionDateOutsidePlanRange() {
        User user = userRepository.save(new User("import-range@cavale.run", "hash", "Importer"));
        String csv = HEADER
                + "PLAN,Range plan,,2026-07-06,2026-11-29,,,,,,,,,,,,,,,,,\n"
                + "WEEK,,,2026-07-06,,1,,BUILD,,,,,,,,,,,,,,\n"
                + "SESSION,,,,,1,,,,,,,2027-01-15,0,RUN,Too late,,,,,,\n";

        assertThatThrownBy(() -> importService.importPlan(user.getId(), new StringReader(csv)))
                .isInstanceOf(PlanImportException.class)
                .hasMessageContaining("outside the plan range");
    }
}
