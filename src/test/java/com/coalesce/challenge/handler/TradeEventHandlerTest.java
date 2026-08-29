package com.coalesce.challenge.handler;

import com.coalesce.challenge.alert.AlertManager;
import com.coalesce.challenge.alert.AlertSeverity;
import com.coalesce.challenge.domain.PositionKey;
import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.engine.*;
import com.coalesce.challenge.event.PriceEvent;
import com.coalesce.challenge.event.TradeEvent;
import com.coalesce.challenge.state.EngineState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeEventHandlerTest {
    private static final Instant TRADE_TIMESTAMP =
            Instant.parse("2026-08-01T01:00:00Z");
    private static final PositionKey POSITION_KEY =
            new PositionKey("TRADER", "BINANCE", "ACCOUNT", "BTCUSDT");

    @Test
    void GIVEN_usdtFee_WHEN_tradeHandled_THEN_updatesPositionAndAddsFeeDirectly() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        TradeEventHandler handler = handler(engineState);

        handler.handle(trade("2", "USDT"));

        PositionState position = engineState.positions().get(POSITION_KEY);
        assertDecimal("1", position.quantity());
        assertDecimal("100", position.averageEntryPrice());
        assertDecimal("2", position.fees());
        assertTrue(position.feesAvailable());
    }

    @Test
    void GIVEN_historicalDuplicateRetainedInCache_WHEN_handled_THEN_alertsWithoutReapplying() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        RecordingAlertManager alerts = new RecordingAlertManager();
        TradeEventHandler handler = new TradeEventHandler(
                engineState, new PositionReplayService(engineState), alerts
        );
        TradeEvent original = trade("T1", TRADE_TIMESTAMP, "1", "2");
        TradeEvent intervening = trade(
                "T2", TRADE_TIMESTAMP.plusSeconds(1), "1", "3"
        );
        TradeEvent retry = trade("T1", TRADE_TIMESTAMP, "1", "2");

        handler.handle(original);
        handler.handle(intervening);
        handler.handle(retry);

        PositionState position = engineState.positions().get(POSITION_KEY);
        assertDecimal("2", position.quantity());
        assertDecimal("5", position.fees());
        assertEquals(
                List.of(new RaisedAlert(
                        AlertSeverity.NEED_TO_BE_NOTIFIED,
                        "Duplicate trade detected for venue=BINANCE, tradeId=T1"
                )),
                alerts.raisedAlerts()
        );
    }

    @Test
    void GIVEN_cachedTradeIdWithChangedPayload_WHEN_handled_THEN_alertsWithoutApplying() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        RecordingAlertManager alerts = new RecordingAlertManager();
        TradeEventHandler handler = new TradeEventHandler(
                engineState, new PositionReplayService(engineState), alerts
        );
        TradeEvent original = trade("T1", TRADE_TIMESTAMP, "1", "2");
        TradeEvent intervening = trade(
                "T2", TRADE_TIMESTAMP.plusSeconds(1), "1", "3"
        );
        TradeEvent conflicting = trade("T1", TRADE_TIMESTAMP, "2", "2");

        handler.handle(original);
        handler.handle(intervening);
        handler.handle(conflicting);

        PositionState position = engineState.positions().get(POSITION_KEY);
        assertDecimal("2", position.quantity());
        assertDecimal("5", position.fees());
        assertEquals(
                List.of(new RaisedAlert(
                        AlertSeverity.REQUIRES_IMMEDIATE_ACTION,
                        "Conflicting trade payload detected for venue=BINANCE, tradeId=T1"
                )),
                alerts.raisedAlerts()
        );
    }

    @Test
    void GIVEN_sameTradeIdAtDifferentVenue_WHEN_handled_THEN_acceptsBothWithoutAlert() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        RecordingAlertManager alerts = new RecordingAlertManager();
        TradeEventHandler handler = new TradeEventHandler(
                engineState, new PositionReplayService(engineState), alerts
        );
        TradeEvent binanceTrade = trade("BINANCE", "T1", TRADE_TIMESTAMP, "1", "2");
        TradeEvent okxTrade = trade("OKX", "T1", TRADE_TIMESTAMP, "1", "3");

        handler.handle(binanceTrade);
        handler.handle(okxTrade);

        assertDecimal("1", engineState.positions().get(POSITION_KEY).quantity());
        PositionKey okxPositionKey = new PositionKey(
                POSITION_KEY.trader(), "OKX", POSITION_KEY.venueAccount(), POSITION_KEY.symbol()
        );
        assertDecimal("1", engineState.positions().get(okxPositionKey).quantity());
        assertTrue(alerts.raisedAlerts().isEmpty());
    }

    @Test
    void GIVEN_sameTradeIdentityForDifferentPositionKeys_WHEN_handled_THEN_rejectsConflictingCorrection() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        RecordingAlertManager alerts = new RecordingAlertManager();
        TradeEventHandler handler = new TradeEventHandler(engineState, new PositionReplayService(engineState), alerts);
        TradeEvent firstAccountTrade = trade(
                "T1", TRADE_TIMESTAMP, "1", "2"
        );
        TradeEvent secondAccountTrade = new TradeEvent(
                TRADE_TIMESTAMP,
                POSITION_KEY.venue(),
                "T1",
                POSITION_KEY.trader(),
                "SECOND_ACCOUNT",
                POSITION_KEY.symbol(),
                Side.BUY,
                BigDecimal.ONE,
                new BigDecimal("100"),
                new BigDecimal("3"),
                "USDT"
        );

        handler.handle(firstAccountTrade);
        handler.handle(secondAccountTrade);

        assertDecimal("1", engineState.positions().get(POSITION_KEY).quantity());
        PositionKey secondAccountKey = new PositionKey(
                POSITION_KEY.trader(),
                POSITION_KEY.venue(),
                "SECOND_ACCOUNT",
                POSITION_KEY.symbol()
        );
        assertFalse(engineState.positions().containsKey(secondAccountKey));
        assertEquals(
                List.of(new RaisedAlert(
                        AlertSeverity.REQUIRES_IMMEDIATE_ACTION,
                        "Conflicting trade payload detected for venue=BINANCE, tradeId=T1"
                )),
                alerts.raisedAlerts()
        );
    }

    @Test
    void GIVEN_tradeAtReplayBoundary_WHEN_handled_THEN_rejectsAndAlertsImmediateAction() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory(2));
        RecordingAlertManager alerts = new RecordingAlertManager();
        TradeEventHandler handler = handler(engineState, alerts);
        handler.handle(trade(
                "T1", TRADE_TIMESTAMP.plusSeconds(1), "1", "1"
        ));
        handler.handle(trade(
                "T2", TRADE_TIMESTAMP.plusSeconds(2), "1", "1"
        ));
        handler.handle(trade(
                "T3", TRADE_TIMESTAMP.plusSeconds(3), "1", "1"
        ));

        handler.handle(trade(
                "AT_BOUNDARY", TRADE_TIMESTAMP.plusSeconds(1), "1", "1"
        ));

        assertDecimal("3", engineState.positions().get(POSITION_KEY).quantity());
        assertEquals(
                List.of(new RaisedAlert(
                        AlertSeverity.REQUIRES_IMMEDIATE_ACTION,
                        "Late trade is at or older than the replay boundary for "
                                + "trader=TRADER, venue=BINANCE, venueAccount=ACCOUNT, "
                                + "symbol=BTCUSDT, tradeId=AT_BOUNDARY, "
                                + "timestamp=2026-08-01T01:00:01Z, "
                                + "replayBoundary=2026-08-01T01:00:01Z"
                )),
                alerts.raisedAlerts()
        );
    }

    @Test
    void GIVEN_nonUsdtFeeAndEligiblePrice_WHEN_tradeHandled_THEN_addsConvertedFee() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        engineState.priceBook().add(new PriceEvent(
                TRADE_TIMESTAMP.minusSeconds(1), "BNBUSDT", new BigDecimal("800")
        ));
        TradeEventHandler handler = handler(engineState);

        handler.handle(trade("0.01", "BNB"));

        PositionState position = engineState.positions().get(POSITION_KEY);
        assertDecimal("8", position.fees());
        assertTrue(position.feesAvailable());
    }

    @Test
    void GIVEN_nonUsdtFeeWithoutPrice_WHEN_tradeHandled_THEN_marksFeeUnavailable() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        TradeEventHandler handler = handler(engineState);

        handler.handle(trade("0.01", "BNB"));

        PositionState position = engineState.positions().get(POSITION_KEY);
        assertDecimal("0", position.fees());
        assertFalse(position.feesAvailable());
    }

    private TradeEvent trade(String fee, String feeAsset) {
        return trade("T1", TRADE_TIMESTAMP, "1", fee, feeAsset);
    }

    private TradeEvent trade(
            String id,
            Instant timestamp,
            String quantity,
            String fee
    ) {
        return trade(id, timestamp, quantity, fee, "USDT");
    }

    private TradeEvent trade(
            String id,
            Instant timestamp,
            String quantity,
            String fee,
            String feeAsset
    ) {
        return trade(POSITION_KEY.venue(), id, timestamp, quantity, fee, feeAsset);
    }

    private TradeEvent trade(
            String venue,
            String id,
            Instant timestamp,
            String quantity,
            String fee
    ) {
        return trade(venue, id, timestamp, quantity, fee, "USDT");
    }

    private TradeEvent trade(
            String venue,
            String id,
            Instant timestamp,
            String quantity,
            String fee,
            String feeAsset
    ) {
        return new TradeEvent(
                timestamp,
                venue,
                id,
                POSITION_KEY.trader(),
                POSITION_KEY.venueAccount(),
                POSITION_KEY.symbol(),
                Side.BUY,
                new BigDecimal(quantity),
                new BigDecimal("100"),
                new BigDecimal(fee),
                feeAsset
        );
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros());
    }

    private TradeEventHandler handler(EngineState engineState) {
        return new TradeEventHandler(
                engineState, new PositionReplayService(engineState), new RecordingAlertManager()
        );
    }

    private TradeEventHandler handler(
            EngineState engineState,
            AlertManager alertManager
    ) {
        return new TradeEventHandler(engineState, new PositionReplayService(engineState), alertManager);
    }

    private record RaisedAlert(AlertSeverity severity, String message) {
    }

    private static final class RecordingAlertManager implements AlertManager {
        private final List<RaisedAlert> raisedAlerts = new ArrayList<>();

        @Override
        public void raise(AlertSeverity severity, String message) {
            raisedAlerts.add(new RaisedAlert(severity, message));
        }

        private List<RaisedAlert> raisedAlerts() {
            return raisedAlerts;
        }
    }

}
