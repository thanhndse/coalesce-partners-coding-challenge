package com.coalesce.challenge.event;

import com.coalesce.challenge.domain.PositionKey;
import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.util.Decimals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record TradeEvent(
    Instant timestamp,
    String venue,
    String tradeId,
    String trader,
    String venueAccount,
    String symbol,
    Side side,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal fee,
    String feeAsset
) implements Event {
    public TradeEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(trader, "trader");
        Objects.requireNonNull(venueAccount, "venueAccount");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(feeAsset, "feeAsset");
        quantity = Decimals.requirePositive(quantity, "quantity");
        price = Decimals.requirePositive(price, "price");
        fee = Decimals.requireNonNegative(fee, "fee");
    }

    @Override
    public EventType type() {
        return EventType.TRADE;
    }

    @Override
    public String identity() {
        return "TRADE:" + venue + ":" + tradeId;
    }

    public PositionKey positionKey() {
        return new PositionKey(trader, venue, venueAccount, symbol);
    }
}
