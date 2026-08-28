package com.coalesce.challenge.event;

import com.coalesce.challenge.util.Decimals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record PriceEvent(
    Instant timestamp,
    String symbol,
    BigDecimal price
) implements Event {
    public PriceEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(symbol, "symbol");
        price = Decimals.requirePositive(price, "price");
    }

    @Override
    public EventType type() {
        return EventType.PRICE;
    }

    @Override
    public String identity() {
        return "PRICE:" + symbol + ":" + timestamp;
    }
}
