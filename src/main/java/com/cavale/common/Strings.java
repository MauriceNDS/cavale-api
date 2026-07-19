package com.cavale.common;

/** Small string helpers shared across services. */
public final class Strings {

    private Strings() {
    }

    /** Trim, mapping null/blank to null — the canonical "optional text" cleanup. */
    public static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
