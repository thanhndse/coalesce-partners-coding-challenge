package com.coalesce.challenge.exception;

import java.nio.file.Path;

public final class CsvDataException extends IllegalArgumentException {
    public CsvDataException(Path path, int lineNumber, String message, Throwable cause) {
        super(path + ":" + lineNumber + ": " + message, cause);
    }

    public CsvDataException(Path path, int lineNumber, String message) {
        super(path + ":" + lineNumber + ": " + message);
    }
}
