package com.cavale.training.domain;

public enum SessionStatus {
    PLANNED, DONE, SKIPPED, MOVED;

    /**
     * The work is still owed. MOVED only records that the session was
     * rescheduled — it is every bit as pending as PLANNED, so anything that
     * asks "is there still something to do here?" must accept both. Only DONE
     * and SKIPPED close a session.
     */
    public boolean isPending() {
        return this == PLANNED || this == MOVED;
    }
}
