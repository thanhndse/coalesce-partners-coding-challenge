package com.coalesce.challenge.engine;

import com.coalesce.challenge.event.Event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PositionTimeline {
    private final PositionState checkpoint;
    private final List<Event> events = new ArrayList<>();
    private Instant replayBoundary;

    public PositionTimeline(PositionState checkpoint) {
        this.checkpoint = checkpoint;
    }

    void insert(Event retainedEvent) {
        int low = 0;
        int high = events.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (retainedEvent.timestamp().isBefore(events.get(middle).timestamp())) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        events.add(low, retainedEvent);
    }

    public boolean isBeforeLatest(Instant timestamp) {
        return !events.isEmpty()
                && timestamp.isBefore(events.getLast().timestamp());
    }

    public boolean isAtOrBeforeReplayBoundary(Instant timestamp) {
        return replayBoundary != null && !timestamp.isAfter(replayBoundary);
    }

    public void advanceReplayBoundary(Instant timestamp) {
        if (replayBoundary == null || timestamp.isAfter(replayBoundary)) {
            replayBoundary = timestamp;
        }
    }

    Event removeOldest() {
        return events.removeFirst();
    }

    public int size() {
        return events.size();
    }

    public PositionState checkpoint() {
        return checkpoint;
    }

    List<Event> events() {
        return events;
    }

    public Instant replayBoundary() {
        return replayBoundary;
    }
}
