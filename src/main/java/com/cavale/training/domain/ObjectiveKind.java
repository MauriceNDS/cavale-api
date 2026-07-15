package com.cavale.training.domain;

/**
 * The terrain an objective is raced on, which decides how its targets and
 * predictions are expressed: ROAD objectives in pace ranges (e.g. 4:20–4:40
 * /km), TRAIL objectives in time + km-effort (km + D+/100) + D+.
 */
public enum ObjectiveKind {
    ROAD,
    TRAIL
}
