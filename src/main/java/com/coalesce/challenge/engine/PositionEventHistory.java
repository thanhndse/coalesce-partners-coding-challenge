package com.coalesce.challenge.engine;

import com.coalesce.challenge.domain.PositionKey;
import com.coalesce.challenge.event.Event;
import com.coalesce.challenge.event.FundingEvent;
import com.coalesce.challenge.event.TradeEvent;
import com.google.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores a bounded event window and its preceding checkpoint for each position.
 */
public final class PositionEventHistory {
    private static final int DEFAULT_MAXIMUM_EVENTS_PER_POSITION = 1_000;
    private static final int DEFAULT_MAXIMUM_KNOWN_EVENTS = 10_000;

    private final int maximumEventsPerPosition;
    private final Map<PositionKey, PositionTimeline> timelines = new HashMap<>();
    private final int maximumKnownEvents;
    private final Map<Object, Event> knownEventsByIdentity = new LinkedHashMap<>();

    @Inject
    public PositionEventHistory() {
        this(DEFAULT_MAXIMUM_EVENTS_PER_POSITION, DEFAULT_MAXIMUM_KNOWN_EVENTS);
    }

    public PositionEventHistory(int maximumEventsPerPosition) {
        this(maximumEventsPerPosition, DEFAULT_MAXIMUM_KNOWN_EVENTS);
    }

    public PositionEventHistory(
            int maximumEventsPerPosition,
            int maximumKnownEvents
    ) {
        if (maximumEventsPerPosition <= 0) {
            throw new IllegalArgumentException(
                    "Maximum events per position must be positive"
            );
        }
        if (maximumKnownEvents <= 0) {
            throw new IllegalArgumentException(
                    "Maximum known events must be positive"
            );
        }
        this.maximumEventsPerPosition = maximumEventsPerPosition;
        this.maximumKnownEvents = maximumKnownEvents;
    }

    /**
     * Returns a previously accepted trade or funding event by its natural identity.
     * Identity tracking is engine-wide because venue event IDs do not belong to a
     * position partition and must survive bounded replay-history compaction.
     */
    public Event knownEvent(Object identity) {
        return knownEventsByIdentity.get(identity);
    }

    public RetentionResult retainTrade(
            PositionKey key,
            PositionState currentState,
            TradeEvent trade,
            PriceBook priceBook
    ) {
        PositionTimeline timeline = timeline(key, currentState);
        boolean late = timeline.isBeforeLatest(trade.timestamp());
        if (late && timeline.isAtOrBeforeReplayBoundary(trade.timestamp())) {
            return RetentionResult.rejected(timeline.replayBoundary());
        }
        timeline.insert(trade);
        remember(trade);
        return compactAndCreateResult(timeline, late, priceBook);
    }

    public void retainFunding(
            PositionKey key,
            PositionState currentState,
            FundingEvent funding,
            PriceBook priceBook
    ) {
        PositionTimeline timeline = timeline(key, currentState);
        timeline.insert(funding);
        remember(funding);
        compactAndCreateResult(
                timeline, false, priceBook
        );
    }

    public List<AffectedPositionFees> affectedFees(
            PriceBook.PriceUpdate update
    ) {
        List<AffectedPositionFees> affectedPositions = new ArrayList<>();
        for (Map.Entry<PositionKey, PositionTimeline> entry : timelines.entrySet()) {
            PositionTimeline timeline = entry.getValue();
            Instant replayBoundary = timeline.isAtOrBeforeReplayBoundary(
                    update.affectedFromInclusive()
            ) ? timeline.replayBoundary() : null;
            List<TradeEvent> affectedTrades = timeline.events().stream()
                    .filter(TradeEvent.class::isInstance)
                    .map(TradeEvent.class::cast)
                    .filter(trade -> isAffectedFee(trade, update))
                    .toList();
            if (replayBoundary != null || !affectedTrades.isEmpty()) {
                affectedPositions.add(new AffectedPositionFees(
                        entry.getKey(), replayBoundary, affectedTrades
                ));
            }
        }
        return List.copyOf(affectedPositions);
    }

    private RetentionResult compactAndCreateResult(
            PositionTimeline timeline,
            boolean late,
            PriceBook priceBook
    ) {
        if (timeline.size() > maximumEventsPerPosition) {
            Event evicted = timeline.removeOldest();
            timeline.advanceReplayBoundary(evicted.timestamp());
            applyToCheckpoint(timeline.checkpoint(), evicted, priceBook);
        }
        return RetentionResult.accepted(
                late,
                timeline.checkpoint(),
                timeline.events()
        );
    }

    private void remember(Event event) {
        knownEventsByIdentity.put(event.identity(), event);
        if (knownEventsByIdentity.size() > maximumKnownEvents) {
            Object oldestIdentity = knownEventsByIdentity.keySet().iterator().next();
            knownEventsByIdentity.remove(oldestIdentity);
        }
    }

    private void applyToCheckpoint(
            PositionState checkpoint,
            Event event,
            PriceBook priceBook
    ) {
        if (event instanceof TradeEvent trade) {
            PositionCalculator.applyTrade(checkpoint, trade, priceBook);
            return;
        }
        PositionCalculator.applyFunding(checkpoint, (FundingEvent) event);
    }

    private PositionTimeline timeline(
            PositionKey key,
            PositionState currentState
    ) {
        return timelines.computeIfAbsent(
                key, ignored -> new PositionTimeline(currentState.copy())
        );
    }

    private boolean isAffectedFee(
            TradeEvent trade,
            PriceBook.PriceUpdate update
    ) {
        if (!update.symbol().equals(
                ReportingFeeCalculator.conversionSymbol(trade)
        )) {
            return false;
        }
        Instant timestamp = trade.timestamp();
        return !timestamp.isBefore(update.affectedFromInclusive())
                && (update.affectedUntilExclusive() == null
                || timestamp.isBefore(update.affectedUntilExclusive()));
    }

    public record AffectedPositionFees(
            PositionKey positionKey,
            Instant replayBoundary,
            List<TradeEvent> trades
    ) { }
}
