package com.cavale.common.exception;

/**
 * A request that is well-formed but conflicts with a domain rule or the current
 * state of a resource (e.g. deactivating an admin, deleting a plan's main
 * objective). Maps to HTTP 409 — distinct from a 400 malformed request, so
 * clients can tell "you sent nonsense" from "that isn't allowed right now".
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
