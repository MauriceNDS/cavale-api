package com.cavale.training.service;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.dto.CreatePlanRequest;
import com.cavale.training.dto.CreateSessionRequest;
import com.cavale.training.dto.CreateWeekRequest;
import com.cavale.training.dto.ImportResult;

/**
 * Canonical CSV import (see docs/IMPORT-FORMAT.md). Delegates every write to
 * {@link TrainingPlanService} so imported plans follow exactly the same rules
 * as plans built through the API or MCP. Atomic: any error rolls back all of it.
 */
@Service
public class PlanImportService {

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .get();

    private final TrainingPlanService planService;

    public PlanImportService(TrainingPlanService planService) {
        this.planService = planService;
    }

    @Transactional
    public ImportResult importPlan(UUID userId, Reader csv) {
        TrainingPlan plan = null;
        Map<Integer, PlanWeek> weeksByNumber = new HashMap<>();
        int sessions = 0;

        try (CSVParser parser = FORMAT.parse(csv)) {
            for (CSVRecord record : parser) {
                long line = record.getRecordNumber() + 1; // +1 for the header row
                String type = required(record, "type", line);

                switch (type) {
                    case "PLAN" -> {
                        if (plan != null) {
                            throw new PlanImportException(line, "duplicate PLAN row — exactly one is allowed");
                        }
                        plan = planService.createPlan(userId, new CreatePlanRequest(
                                required(record, "name", line),
                                optional(record, "goal"),
                                date(record, "start_date", line, true),
                                date(record, "end_date", line, true)));
                    }
                    case "WEEK" -> {
                        TrainingPlan owner = requirePlan(plan, line);
                        int weekNumber = intValue(record, "week_number", line, true);
                        if (weeksByNumber.containsKey(weekNumber)) {
                            throw new PlanImportException(line, "duplicate week_number " + weekNumber);
                        }
                        PlanWeek week = planService.addWeek(userId, owner.getId(), new CreateWeekRequest(
                                weekNumber,
                                date(record, "start_date", line, true),
                                optional(record, "phase"),
                                weekType(record, line),
                                decimal(record, "target_volume_km", line),
                                intObject(record, "target_elevation_m", line),
                                intObject(record, "target_load_ua", line),
                                optional(record, "focus")));
                        weeksByNumber.put(weekNumber, week);
                    }
                    case "SESSION" -> {
                        TrainingPlan owner = requirePlan(plan, line);
                        int weekNumber = intValue(record, "week_number", line, true);
                        PlanWeek week = weeksByNumber.get(weekNumber);
                        if (week == null) {
                            throw new PlanImportException(line,
                                    "SESSION references week " + weekNumber + " which has no WEEK row above it");
                        }
                        LocalDate sessionDate = date(record, "date", line, true);
                        if (sessionDate.isBefore(owner.getStartDate()) || sessionDate.isAfter(owner.getEndDate())) {
                            throw new PlanImportException(line,
                                    "session date " + sessionDate + " is outside the plan range");
                        }
                        Integer orderInDay = intObject(record, "order_in_day", line);
                        planService.addSession(userId, week.getId(), new CreateSessionRequest(
                                sessionDate,
                                orderInDay == null ? 0 : orderInDay,
                                discipline(record, line),
                                required(record, "title", line),
                                optional(record, "detail"),
                                optional(record, "zone"),
                                intObject(record, "duration_min", line),
                                intObject(record, "elevation_m", line),
                                intObject(record, "rpe_min", line),
                                intObject(record, "rpe_max", line)));
                        sessions++;
                    }
                    default -> throw new PlanImportException(line,
                            "unknown type '" + type + "' (expected PLAN, WEEK, or SESSION)");
                }
            }
        } catch (IOException e) {
            throw new PlanImportException("could not read the CSV file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // commons-csv throws this for a missing/invalid header set
            throw new PlanImportException("invalid CSV header: " + e.getMessage());
        }

        if (plan == null) {
            throw new PlanImportException("file contains no PLAN row");
        }
        return new ImportResult(plan.getId(), weeksByNumber.size(), sessions);
    }

    private static TrainingPlan requirePlan(TrainingPlan plan, long line) {
        if (plan == null) {
            throw new PlanImportException(line, "the PLAN row must come before WEEK and SESSION rows");
        }
        return plan;
    }

    private static String required(CSVRecord record, String column, long line) {
        String value = optional(record, column);
        if (value == null) {
            throw new PlanImportException(line, "missing required value '" + column + "'");
        }
        return value;
    }

    private static String optional(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) return null;
        String value = record.get(column);
        return value == null || value.isBlank() ? null : value;
    }

    private static LocalDate date(CSVRecord record, String column, long line, boolean requiredValue) {
        String value = requiredValue ? required(record, column, line) : optional(record, column);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new PlanImportException(line, "'" + column + "' must be an ISO date (yyyy-MM-dd), got '" + value + "'");
        }
    }

    private static int intValue(CSVRecord record, String column, long line, boolean requiredValue) {
        Integer value = requiredValue
                ? Integer.valueOf(parseInt(required(record, column, line), column, line))
                : intObject(record, column, line);
        return value == null ? 0 : value;
    }

    private static Integer intObject(CSVRecord record, String column, long line) {
        String value = optional(record, column);
        return value == null ? null : parseInt(value, column, line);
    }

    private static int parseInt(String value, String column, long line) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new PlanImportException(line, "'" + column + "' must be an integer, got '" + value + "'");
        }
    }

    private static BigDecimal decimal(CSVRecord record, String column, long line) {
        String value = optional(record, column);
        if (value == null) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new PlanImportException(line, "'" + column + "' must be a number, got '" + value + "'");
        }
    }

    private static WeekType weekType(CSVRecord record, long line) {
        String value = required(record, "week_type", line);
        try {
            return WeekType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new PlanImportException(line, "unknown week_type '" + value + "'");
        }
    }

    private static Discipline discipline(CSVRecord record, long line) {
        String value = required(record, "discipline", line);
        try {
            return Discipline.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new PlanImportException(line, "unknown discipline '" + value + "'");
        }
    }
}
