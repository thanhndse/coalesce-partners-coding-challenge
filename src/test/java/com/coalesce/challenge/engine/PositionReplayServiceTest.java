package com.coalesce.challenge.engine;

import com.coalesce.challenge.domain.PositionKey;
import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.event.FundingEvent;
import com.coalesce.challenge.event.TradeEvent;
import com.coalesce.challenge.state.EngineState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionReplayServiceTest {
    private static final Instant START = Instant.parse("2026-08-01T01:00:00Z");
    private static final PositionKey POSITION_KEY =
            new PositionKey("TRADER", "BINANCE", "ACCOUNT", "BTCUSDT");

    @Test
    void GIVEN_lateTradeInsideHistory_WHEN_replayed_THEN_appliesTradesAndFundingInOrder() {
        ReplayFixture fixture = new ReplayFixture(1_000);
        fixture.applyTradeNormally(
                trade("T1", 0, Side.BUY, "1", "100"), new BigDecimal("1")
        );
        fixture.applyFundingNormally(funding("F1", 1, "-5"));
        fixture.applyTradeNormally(
                trade("T3", 3, Side.SELL, "1", "120"), new BigDecimal("2")
        );

        fixture.replayLateTrade(
                trade("T2", 2, Side.BUY, "1", "110"), new BigDecimal("3")
        );

        PositionState state = fixture.position();
        assertDecimal("1", state.quantity());
        assertDecimal("105", state.averageEntryPrice());
        assertDecimal("15", state.realizedPnl());
        assertDecimal("-5", state.fundingPnl());
        assertDecimal("6", state.fees());
    }

    @Test
    void GIVEN_lateTradeInsideCompactedWindow_WHEN_replayed_THEN_startsFromCheckpoint() {
        ReplayFixture fixture = new ReplayFixture(2);
        fixture.applyTradeNormally(
                trade("T1", 0, Side.BUY, "1", "100"), BigDecimal.ONE
        );
        fixture.applyTradeNormally(
                trade("T3", 3, Side.SELL, "1", "120"), BigDecimal.ONE
        );

        fixture.replayLateTrade(
                trade("T2", 2, Side.BUY, "1", "110"), BigDecimal.ONE
        );

        PositionState state = fixture.position();
        assertDecimal("1", state.quantity());
        assertDecimal("105", state.averageEntryPrice());
        assertDecimal("15", state.realizedPnl());
        assertDecimal("3", state.fees());
    }

    @Test
    void GIVEN_tradeAtReplayBoundary_WHEN_retained_THEN_rejectsItBeforeReplay() {
        ReplayFixture fixture = new ReplayFixture(2);
        fixture.applyTradeNormally(
                trade("T1", 1, Side.BUY, "1", "100"), BigDecimal.ONE
        );
        fixture.applyTradeNormally(
                trade("T2", 2, Side.BUY, "1", "100"), BigDecimal.ONE
        );
        fixture.applyTradeNormally(
                trade("T3", 3, Side.BUY, "1", "100"), BigDecimal.ONE
        );

        RetentionResult retention = fixture.retainTrade(
                trade("AT_BOUNDARY", 1, Side.BUY, "1", "100"), BigDecimal.ONE
        );

        assertFalse(retention.accepted());
        assertEquals(START.plusSeconds(1), retention.replayBoundary());
        assertDecimal("3", fixture.position().quantity());
        assertDecimal("3", fixture.position().fees());
    }

    @Test
    void GIVEN_fundingOlderThanReplayBoundary_WHEN_laterTradeReplayed_THEN_preservesFunding() {
        ReplayFixture fixture = new ReplayFixture(2);
        fixture.applyTradeNormally(
                trade("T1", 1, Side.BUY, "1", "100"), BigDecimal.ONE
        );
        fixture.applyTradeNormally(
                trade("T2", 2, Side.BUY, "1", "110"), BigDecimal.ONE
        );
        fixture.applyTradeNormally(
                trade("T4", 4, Side.SELL, "1", "130"), BigDecimal.ONE
        );
        fixture.applyFundingNormally(funding("F0", 0, "-5"));

        fixture.replayLateTrade(
                trade("T3", 3, Side.BUY, "1", "120"), BigDecimal.ONE
        );

        PositionState state = fixture.position();
        assertDecimal("2", state.quantity());
        assertDecimal("110", state.averageEntryPrice());
        assertDecimal("20", state.realizedPnl());
        assertDecimal("-5", state.fundingPnl());
        assertDecimal("4", state.fees());
    }

    @Test
    void GIVEN_unresolvedFee_WHEN_tradeReplayed_THEN_feeStaysUnresolvedOnce() {
        ReplayFixture fixture = new ReplayFixture(1_000);
        fixture.applyTradeNormally(
                trade("T1", 0, Side.BUY, "1", "100"), null
        );
        fixture.applyTradeNormally(
                trade("T3", 3, Side.BUY, "1", "100"), BigDecimal.ONE
        );

        fixture.replayLateTrade(
                trade("T2", 2, Side.BUY, "1", "100"), BigDecimal.ONE
        );

        PositionState state = fixture.position();
        assertDecimal("2", state.fees());
        assertFalse(state.feesAvailable());
    }

    private TradeEvent trade(
            String id,
            long secondsAfterStart,
            Side side,
            String quantity,
            String price
    ) {
        return new TradeEvent(
                START.plusSeconds(secondsAfterStart),
                POSITION_KEY.venue(),
                id,
                POSITION_KEY.trader(),
                POSITION_KEY.venueAccount(),
                POSITION_KEY.symbol(),
                side,
                new BigDecimal(quantity),
                new BigDecimal(price),
                BigDecimal.ZERO,
                "USDT"
        );
    }

    private FundingEvent funding(String id, long secondsAfterStart, String amount) {
        return new FundingEvent(
                START.plusSeconds(secondsAfterStart),
                id,
                POSITION_KEY.trader(),
                POSITION_KEY.venue(),
                POSITION_KEY.venueAccount(),
                POSITION_KEY.symbol(),
                "USDT",
                new BigDecimal(amount)
        );
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(
                new BigDecimal(expected).stripTrailingZeros(),
                actual.stripTrailingZeros()
        );
    }

    private static final class ReplayFixture {
        private final EngineState engineState;
        private final PositionEventHistory eventHistory;
        private final PositionReplayService replayService;
        private final PriceBook priceBook;

        private ReplayFixture(int maximumEvents) {
            eventHistory = new PositionEventHistory(maximumEvents);
            priceBook = new PriceBook();
            engineState = new EngineState(priceBook, eventHistory);
            replayService = new PositionReplayService(engineState);
        }

        private void applyTradeNormally(
                TradeEvent trade,
                BigDecimal reportingFee
        ) {
            RetentionResult retention = retainTrade(
                    trade, reportingFee
            );
            assertTrue(retention.accepted());
            assertFalse(retention.late());
            PositionCalculator.applyTrade(position(), trade, priceBook);
            if (reportingFee == null) {
                position().addUnresolvedFee();
            } else {
                position().addFee(reportingFee);
            }
        }

        private void applyFundingNormally(FundingEvent funding) {
            PositionState state = position();
            eventHistory.retainFunding(POSITION_KEY, state, funding, priceBook);
            state.addFundingPnl(funding.amount());
        }

        private RetentionResult retainTrade(
                TradeEvent trade,
            BigDecimal reportingFee
        ) {
            TradeEvent retainedTrade = withFee(trade, reportingFee);
            return eventHistory.retainTrade(
                    POSITION_KEY, position(), retainedTrade, priceBook
            );
        }

        private TradeEvent withFee(
                TradeEvent trade,
                BigDecimal reportingFee
        ) {
            return new TradeEvent(
                    trade.timestamp(), trade.venue(), trade.tradeId(), trade.trader(),
                    trade.venueAccount(), trade.symbol(), trade.side(), trade.quantity(),
                    trade.price(), reportingFee == null ? BigDecimal.ONE : reportingFee,
                    reportingFee == null ? "BNB" : "USDT"
            );
        }

        private void replayLateTrade(
                TradeEvent trade,
                BigDecimal reportingFee
        ) {
            RetentionResult retention = retainTrade(
                    trade, reportingFee
            );
            assertTrue(retention.accepted());
            assertTrue(retention.late());
            replayService.replay(POSITION_KEY, retention);
        }

        private PositionState position() {
            return engineState.positions().computeIfAbsent(
                    POSITION_KEY, ignored -> PositionState.empty()
            );
        }
    }
}
