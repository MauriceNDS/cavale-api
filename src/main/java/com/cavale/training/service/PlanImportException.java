package com.cavale.training.service;

public class PlanImportException extends RuntimeException {

    public PlanImportException(long line, String message) {
        super("Line " + line + ": " + message);
    }

    public PlanImportException(String message) {
        super(message);
    }
}
