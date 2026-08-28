package com.coalesce.challenge.domain;

import com.coalesce.challenge.util.Decimals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Position snapshot immediately before event processing begins. */
public record OpeningPosition(
    Instant timestamp,
    String trader,
    String venue,
    String venueAccount,
    String symbol,
    BigDecimal quantity,
    BigDecimal averageEntryPrice
) {
    public OpeningPosition {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(trader, "trader");
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(venueAccount, "venueAccount");
        Objects.requireNonNull(symbol, "symbol");
        quantity = Decimals.normalize(quantity, "quantity");
        averageEntryPrice = Decimals.normalize(averageEntryPrice, "averageEntryPrice");
        if (quantity.signum() != 0 && averageEntryPrice.signum() <= 0) {
            throw new IllegalArgumentException("A non-zero opening position requires a positive entry price");
        }
    }

    public PositionKey key() {
        return new PositionKey(trader, venue, venueAccount, symbol);
    }
}
