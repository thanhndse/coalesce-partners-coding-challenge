package com.coalesce.challenge.event;

import com.coalesce.challenge.domain.FundingKey;
import com.coalesce.challenge.domain.PositionKey;
import com.coalesce.challenge.util.Decimals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record FundingEvent(
    Instant timestamp,
    String eventId,
    String trader,
    String venue,
    String venueAccount,
    String symbol,
    String asset,
    BigDecimal amount
) implements Event {
    public FundingEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(trader, "trader");
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(venueAccount, "venueAccount");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(asset, "asset");
        amount = Decimals.normalize(amount, "amount");
    }

    @Override
    public EventType type() {
        return EventType.FUNDING;
    }

    @Override
    public Object identity() {
        return new FundingKey(venue, eventId);
    }

    public PositionKey positionKey() {
        return new PositionKey(trader, venue, venueAccount, symbol);
    }
}
