package com.coalesce.challenge.engine;

import com.coalesce.challenge.domain.OpeningPosition;
import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.event.TradeEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionCalculatorTest {
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void appliesWeightedAverageAndRealizesLongReduction() {
        PositionState state = PositionState.from(opening("2", "60000"));

        PositionCalculator.apply(state, trade("T1", Side.BUY, "0.5", "61000"));
        PositionCalculator.apply(state, trade("T2", Side.SELL, "0.3", "62000"));

        assertDecimal("2.2", state.quantity());
        assertDecimal("60200", state.averageEntryPrice());
        assertDecimal("540", state.realizedPnl());
    }

    @Test
    void reducesAShortAndThenCrossesThroughZero() {
        PositionState state = PositionState.from(opening("-2", "100"));

        PositionCalculator.apply(state, trade("T1", Side.BUY, "0.5", "90"));
        PositionCalculator.apply(state, trade("T2", Side.BUY, "2", "110"));

        assertDecimal("0.5", state.quantity());
        assertDecimal("110", state.averageEntryPrice());
        assertDecimal("-10", state.realizedPnl());
    }

    @Test
    void exactCloseClearsQuantityAndAverageEntry() {
        PositionState state = PositionState.from(opening("1.25", "80"));

        PositionCalculator.apply(state, trade("T1", Side.SELL, "1.25", "84"));

        assertDecimal("0", state.quantity());
        assertDecimal("0", state.averageEntryPrice());
        assertDecimal("5", state.realizedPnl());
    }

    private OpeningPosition opening(String quantity, String averageEntryPrice) {
        return new OpeningPosition(
            START, "TRADER", "VENUE", "ACCOUNT", "BTCUSDT",
            new BigDecimal(quantity), new BigDecimal(averageEntryPrice)
        );
    }

    private TradeEvent trade(String id, Side side, String quantity, String price) {
        return new TradeEvent(
            START.plusSeconds(id.equals("T1") ? 1 : 2),
            "VENUE", id, "TRADER", "ACCOUNT", "BTCUSDT", side,
            new BigDecimal(quantity), new BigDecimal(price), BigDecimal.ZERO, "USDT"
        );
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
