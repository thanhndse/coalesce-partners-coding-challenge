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
    void GIVEN_flatPosition_WHEN_sellTradeApplied_THEN_opensShortAtTradePrice() {
        PositionState state = PositionState.empty();

        applyTrade(state, trade("T1", Side.SELL, "2", "100"));

        assertDecimal("-2", state.quantity());
        assertDecimal("100", state.averageEntryPrice());
        assertDecimal("0", state.realizedPnl());
    }

    @Test
    void GIVEN_longPosition_WHEN_increaseAndReductionApplied_THEN_updatesCostAndPnl() {
        PositionState state = PositionState.from(opening("2", "60000"));

        applyTrade(state, trade("T1", Side.BUY, "0.5", "61000"));
        applyTrade(state, trade("T2", Side.SELL, "0.3", "62000"));

        assertDecimal("2.2", state.quantity());
        assertDecimal("60200", state.averageEntryPrice());
        assertDecimal("540", state.realizedPnl());
    }

    @Test
    void GIVEN_shortPosition_WHEN_tradesCrossZero_THEN_opensLongRemainder() {
        PositionState state = PositionState.from(opening("-2", "100"));

        applyTrade(state, trade("T1", Side.BUY, "0.5", "90"));
        applyTrade(state, trade("T2", Side.BUY, "2", "110"));

        assertDecimal("0.5", state.quantity());
        assertDecimal("110", state.averageEntryPrice());
        assertDecimal("-10", state.realizedPnl());
    }

    @Test
    void GIVEN_openPosition_WHEN_tradeClosesExactly_THEN_clearsQuantityAndAverage() {
        PositionState state = PositionState.from(opening("1.25", "80"));

        applyTrade(state, trade("T1", Side.SELL, "1.25", "84"));

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

    private void applyTrade(PositionState state, TradeEvent trade) {
        PositionCalculator.applyTrade(state, trade, new PriceBook());
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros());
    }
}
