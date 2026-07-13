package com.cavale.user.domain;

/**
 * Current availability of the athlete. Drives what a coach (human or MCP)
 * may plan: an INJURED athlete gets recovery, not a shock block.
 */
public enum AthleteStatus {
    AVAILABLE,
    INJURED,
    RECOVERING,
    SICK
}
