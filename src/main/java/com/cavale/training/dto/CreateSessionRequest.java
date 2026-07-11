package com.cavale.training.dto;

import java.time.LocalDate;

import com.cavale.training.domain.Discipline;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSessionRequest(

        @NotNull(message = "Date is required")
        LocalDate date,

        @Min(0) int orderInDay,

        @NotNull(message = "Discipline is required")
        Discipline discipline,

        @NotBlank(message = "Title is required")
        @Size(max = 200)
        String title,

        String detail,

        @Size(max = 30)
        String zone,

        @Min(0) Integer durationMin,
        @Min(0) Integer elevationM,
        @Min(0) @Max(10) Integer rpeMin,
        @Min(0) @Max(10) Integer rpeMax,
        java.util.List<com.cavale.training.workout.WorkoutStructure.Node> workout) {
}
