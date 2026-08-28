package com.coalesce.challenge.engine;

import com.coalesce.challenge.domain.OpeningPosition;
import com.coalesce.challenge.domain.PnlReport;
import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.event.PriceEvent;
import com.coalesce.challenge.event.TradeEvent;
import com.coalesce.challenge.exception.ConflictingEventException;
import com.coalesce.challenge.exception.LateEventException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PnlEngineTest {
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void exactDuplicateIsANoOpAndChangedContentIsAConflict() {
        PnlEngine engine = initializedEngine(opening("ACCOUNT", "1", "100"));
        TradeEvent original = trade("T1", "ACCOUNT", START.plusSeconds(10), Side.BUY,
            "1", "110", "0", "USDT");

        assertEquals(ProcessResult.APPLIED, engine.process(original));
        assertEquals(ProcessResult.DUPLICATE, engine.process(original));

        TradeEvent correction = trade("T1", "ACCOUNT", START.plusSeconds(10), Side.BUY,
            "2", "110", "0", "USDT");
        assertThrows(ConflictingEventException.class, () -> engine.process(correction));
    }

    @Test
    void rejectsLateEventsOnlyForTheAffectedPositionPartition() {
        PnlEngine engine = initializedEngine(opening("ACCOUNT_A", "1", "100"));
        engine.process(trade("T2", "ACCOUNT_A", START.plusSeconds(20), Side.BUY,
            "1", "100", "0", "USDT"));

        assertThrows(LateEventException.class, () -> engine.process(
            trade("T1", "ACCOUNT_A", START.plusSeconds(10), Side.BUY,
                "1", "100", "0", "USDT")
        ));

        assertEquals(ProcessResult.APPLIED, engine.process(
            trade("T3", "ACCOUNT_B", START.plusSeconds(10), Side.BUY,
                "1", "100", "0", "USDT")
        ));
    }

    @Test
    void convertsNonUsdtFeesUsingTheLatestEligiblePrice() {
        PnlEngine engine = initializedEngine();
        engine.process(price("BNBUSDT", START.plusSeconds(5), "800"));
        engine.process(trade("T1", "ACCOUNT", START.plusSeconds(10), Side.BUY,
            "1", "100", "0.01", "BNB"));
        engine.process(price("BTCUSDT", START.plusSeconds(20), "110"));

        PnlReport report = engine.report(START.plusSeconds(20)).getFirst();

        assertDecimal("8", report.fees().orElseThrow());
        assertDecimal("10", report.unrealizedPnl().orElseThrow());
        assertDecimal("2", report.totalPnl().orElseThrow());
    }

    @Test
    void leavesTotalUnavailableUntilAMissingFeePriceCanBeResolved() {
        PnlEngine engine = initializedEngine();
        engine.process(trade("T1", "ACCOUNT", START.plusSeconds(10), Side.BUY,
            "1", "100", "0.01", "BNB"));
        engine.process(price("BTCUSDT", START.plusSeconds(20), "110"));

        assertTrue(engine.report(START.plusSeconds(20)).getFirst().fees().isEmpty());
        assertTrue(engine.report(START.plusSeconds(20)).getFirst().totalPnl().isEmpty());

        engine.process(price("BNBUSDT", START.plusSeconds(5), "800"));
        PnlReport resolved = engine.report(START.plusSeconds(20)).getFirst();
        assertDecimal("8", resolved.fees().orElseThrow());
        assertDecimal("2", resolved.totalPnl().orElseThrow());
    }

    @Test
    void reportsMissingMarkAsUnavailableButKeepsOtherComponents() {
        PnlEngine engine = initializedEngine(opening("ACCOUNT", "1", "100"));

        PnlReport report = engine.report(START.plusSeconds(100)).getFirst();

        assertTrue(report.unrealizedPnl().isEmpty());
        assertTrue(report.totalPnl().isEmpty());
        assertDecimal("0", report.realizedPnl());
        assertDecimal("0", report.fees().orElseThrow());
    }

    @Test
    void valuesAccountsIndependentlyBeforeAggregatingTraderAndSymbol() {
        PnlEngine engine = initializedEngine(
            opening("LONG_ACCOUNT", "1", "100"),
            opening("SHORT_ACCOUNT", "-1", "90")
        );
        engine.process(price("BTCUSDT", START.plusSeconds(10), "110"));

        PnlReport report = engine.report(START.plusSeconds(10)).getFirst();

        assertDecimal("0", report.finalQuantity());
        assertDecimal("-10", report.unrealizedPnl().orElseThrow());
    }

    private PnlEngine initializedEngine(OpeningPosition... openings) {
        PnlEngine engine = new PnlEngine();
        engine.initialize(List.of(openings));
        return engine;
    }

    private OpeningPosition opening(String account, String quantity, String average) {
        return new OpeningPosition(
            START, "TRADER", "VENUE", account, "BTCUSDT",
            new BigDecimal(quantity), new BigDecimal(average)
        );
    }

    private TradeEvent trade(
        String id,
        String account,
        Instant timestamp,
        Side side,
        String quantity,
        String price,
        String fee,
        String feeAsset
    ) {
        return new TradeEvent(
            timestamp, "VENUE", id, "TRADER", account, "BTCUSDT", side,
            new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(fee), feeAsset
        );
    }

    private PriceEvent price(String symbol, Instant timestamp, String price) {
        return new PriceEvent(timestamp, symbol, new BigDecimal(price));
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
