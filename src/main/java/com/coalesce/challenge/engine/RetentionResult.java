package com.coalesce.challenge.engine;

import com.coalesce.challenge.event.Event;

import java.time.Instant;
import java.util.List;

public final class RetentionResult {
    public final boolean accepted;
    public final boolean late;
    public final PositionState checkpoint;
    public final List<Event> retainedEvents;
    public final Instant replayBoundary;

    public RetentionResult(
            boolean accepted,
            boolean late,
            PositionState checkpoint,
            List<Event> retainedEvents,
            Instant replayBoundary
    ) {
        this.accepted = accepted;
        this.late = late;
        this.checkpoint = checkpoint;
        this.retainedEvents = retainedEvents;
        this.replayBoundary = replayBoundary;
    }

    public static RetentionResult accepted(
            boolean late,
            PositionState checkpoint,
            List<Event> retainedEvents
    ) {
        return new RetentionResult(
                true,
                late,
                checkpoint,
                List.copyOf(retainedEvents),
                null
        );
    }

    public static RetentionResult rejected(Instant replayBoundary) {
        return new RetentionResult(
                false,
                true,
                null,
                List.of(),
                replayBoundary
        );
    }

    public boolean accepted() {
        return accepted;
    }

    public boolean late() {
        return late;
    }

    public Instant replayBoundary() {
        return replayBoundary;
    }

    PositionState checkpoint() {
        return checkpoint;
    }

    List<Event> retainedEvents() {
        return retainedEvents;
    }
}
