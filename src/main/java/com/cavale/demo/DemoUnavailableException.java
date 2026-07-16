package com.cavale.demo;

/** The demo is disabled, or at capacity — try again later. */
public class DemoUnavailableException extends RuntimeException {
    public DemoUnavailableException(String message) {
        super(message);
    }
}
