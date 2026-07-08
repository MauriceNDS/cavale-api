package com.cavale.common.exception;

/**
 * Thrown when a resource doesn't exist OR belongs to another user — both map
 * to 404 so the API never leaks whether someone else's resource exists.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}
