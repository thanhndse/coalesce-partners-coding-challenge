package com.coalesce.challenge.event;

import java.time.Instant;
import java.util.Comparator;

public sealed interface Event permits TradeEvent, FundingEvent, PriceEvent {
    Comparator<Event> ORDERING = Comparator
        .comparing(Event::timestamp)
        .thenComparingInt(event -> event.type().priority());

    Instant timestamp();

    EventType type();

    /** Natural event identity, including its event type to avoid cross-type collisions. */
    Object identity();
}
