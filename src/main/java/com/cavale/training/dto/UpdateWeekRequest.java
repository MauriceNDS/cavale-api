package com.cavale.training.dto;

/** Partial update — currently only the free-text focus/description is editable. */
public record UpdateWeekRequest(String focus) {
}
