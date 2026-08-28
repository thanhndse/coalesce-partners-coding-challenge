package com.coalesce.challenge.exception;

import java.time.Instant;

public final class LateEventException extends IllegalStateException {
    public LateEventException(String partition, Instant eventTimestamp, Instant watermark) {
        super("Late event for " + partition + ": timestamp " + eventTimestamp
            + " precedes watermark " + watermark);
    }
}
