package com.coalesce.challenge.exception;

public final class ConflictingEventException extends IllegalArgumentException {
    public ConflictingEventException(String identity) {
        super("Conflicting correction for known event " + identity);
    }
}
