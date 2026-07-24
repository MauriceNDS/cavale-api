package com.cavale.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Names the app that will hold the token ("Claude Code on the laptop"…). */
public record IssuePatRequest(

        @NotBlank(message = "Label is required")
        @Size(max = 100, message = "Label must not exceed 100 characters")
        String label) {
}
