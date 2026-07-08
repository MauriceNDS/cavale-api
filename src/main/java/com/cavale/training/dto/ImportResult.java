package com.cavale.training.dto;

import java.util.UUID;

public record ImportResult(UUID planId, int weeksCreated, int sessionsCreated) {
}
