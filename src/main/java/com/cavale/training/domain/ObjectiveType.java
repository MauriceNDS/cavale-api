package com.cavale.training.domain;

/** What kind of goal an objective is — a race, or a non-race training goal. */
public enum ObjectiveType {
    RACE,
    /** Coming back from injury. */
    RECOVERY,
    /** Getting (back) in shape. */
    FITNESS,
    /** Open-ended structured training. */
    GENERAL
}
