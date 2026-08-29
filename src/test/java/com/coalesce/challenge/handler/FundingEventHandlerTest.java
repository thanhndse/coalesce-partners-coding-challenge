package com.coalesce.challenge.handler;

import com.coalesce.challenge.alert.AlertManager;
import com.coalesce.challenge.alert.AlertSeverity;
import com.coalesce.challenge.domain.PositionKey;
import com.coalesce.challenge.engine.PositionEventHistory;
import com.coalesce.challenge.engine.PositionState;
import com.coalesce.challenge.engine.PriceBook;
import com.coalesce.challenge.event.FundingEvent;
import com.coalesce.challenge.state.EngineState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundingEventHandlerTest {
    private static final Instant FUNDING_TIMESTAMP =
        Instant.parse("2026-08-01T08:00:00Z");
    private static final PositionKey POSITION_KEY =
        new PositionKey("TRADER", "BINANCE", "ACCOUNT", "BTCUSDT");

    @Test
    void GIVEN_validUsdtFunding_WHEN_handled_THEN_addsFundingPnlWithoutAlert() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        RecordingAlertManager alerts = new RecordingAlertManager();
        FundingEventHandler handler = new FundingEventHandler(
            engineState, alerts
        );

        handler.handle(funding("F1", FUNDING_TIMESTAMP, "-5"));

        assertDecimal("-5", position(engineState, POSITION_KEY).fundingPnl());
        assertTrue(alerts.raisedAlerts().isEmpty());
    }

    @Test
    void GIVEN_historicalDuplicateRetainedInCache_WHEN_handled_THEN_alertsWithoutReapplying() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        RecordingAlertManager alerts = new RecordingAlertManager();
        FundingEventHandler handler = new FundingEventHandler(
            engineState, alerts
        );
        FundingEvent original = funding("F1", FUNDING_TIMESTAMP, "-5");
        FundingEvent intervening = funding(
            "F2", FUNDING_TIMESTAMP.plusSeconds(1), "-2"
        );
        FundingEvent retry = funding("F1", FUNDING_TIMESTAMP, "-5.00");

        handler.handle(original);
        handler.handle(intervening);
        handler.handle(retry);

        assertDecimal("-7", position(engineState, POSITION_KEY).fundingPnl());
        assertEquals(
            List.of(new RaisedAlert(
                AlertSeverity.NEED_TO_BE_NOTIFIED,
                "Duplicate funding event detected for venue=BINANCE, eventId=F1"
            )),
            alerts.raisedAlerts()
        );
    }

    @Test
    void GIVEN_cachedEventIdWithChangedPayload_WHEN_handled_THEN_alertsWithoutApplying() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        RecordingAlertManager alerts = new RecordingAlertManager();
        FundingEventHandler handler = new FundingEventHandler(
            engineState, alerts
        );
        FundingEvent original = funding("F1", FUNDING_TIMESTAMP, "-5");
        FundingEvent intervening = funding(
            "F2", FUNDING_TIMESTAMP.plusSeconds(1), "-2"
        );
        FundingEvent conflicting = funding("F1", FUNDING_TIMESTAMP, "-3");

        handler.handle(original);
        handler.handle(intervening);
        handler.handle(conflicting);

        assertDecimal("-7", position(engineState, POSITION_KEY).fundingPnl());
        assertEquals(
            List.of(new RaisedAlert(
                AlertSeverity.REQUIRES_IMMEDIATE_ACTION,
                "Conflicting funding event payload detected for venue=BINANCE, eventId=F1"
            )),
            alerts.raisedAlerts()
        );
    }

    @Test
    void GIVEN_sameEventIdAtDifferentVenue_WHEN_handled_THEN_acceptsBothWithoutAlert() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        RecordingAlertManager alerts = new RecordingAlertManager();
        FundingEventHandler handler = new FundingEventHandler(
            engineState, alerts
        );
        FundingEvent binanceFunding = funding(
            "BINANCE", "F1", FUNDING_TIMESTAMP, "-5"
        );
        FundingEvent okxFunding = funding("OKX", "F1", FUNDING_TIMESTAMP, "-3");

        handler.handle(binanceFunding);
        handler.handle(okxFunding);

        assertDecimal("-5", position(engineState, POSITION_KEY).fundingPnl());
        PositionKey okxPositionKey = new PositionKey(
            POSITION_KEY.trader(), "OKX", POSITION_KEY.venueAccount(), POSITION_KEY.symbol()
        );
        assertDecimal("-3", position(engineState, okxPositionKey).fundingPnl());
        assertTrue(alerts.raisedAlerts().isEmpty());
    }

    @Test
    void GIVEN_sameFundingIdentityForDifferentPositionKeys_WHEN_handled_THEN_rejectsConflictingCorrection() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        RecordingAlertManager alerts = new RecordingAlertManager();
        FundingEventHandler handler = new FundingEventHandler(engineState, alerts);
        FundingEvent original = funding("F1", FUNDING_TIMESTAMP, "-5");
        FundingEvent conflicting = new FundingEvent(
            FUNDING_TIMESTAMP,
            "F1",
            POSITION_KEY.trader(),
            POSITION_KEY.venue(),
            "SECOND_ACCOUNT",
            POSITION_KEY.symbol(),
            "USDT",
            new BigDecimal("-3")
        );

        handler.handle(original);
        handler.handle(conflicting);

        assertDecimal("-5", position(engineState, POSITION_KEY).fundingPnl());
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
                "Conflicting funding event payload detected for venue=BINANCE, eventId=F1"
            )),
            alerts.raisedAlerts()
        );
    }

    @Test
    void GIVEN_duplicateFundingEvictedFromReplayHistory_WHEN_handled_THEN_doesNotReapplyIt() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory(2));
        RecordingAlertManager alerts = new RecordingAlertManager();
        FundingEventHandler handler = new FundingEventHandler(engineState, alerts);

        handler.handle(funding("F1", FUNDING_TIMESTAMP, "-5"));
        handler.handle(funding("F2", FUNDING_TIMESTAMP.plusSeconds(1), "-2"));
        handler.handle(funding("F3", FUNDING_TIMESTAMP.plusSeconds(2), "-3"));
        handler.handle(funding("F1", FUNDING_TIMESTAMP, "-5.00"));

        assertDecimal("-10", position(engineState, POSITION_KEY).fundingPnl());
        assertEquals(
            List.of(new RaisedAlert(
                AlertSeverity.NEED_TO_BE_NOTIFIED,
                "Duplicate funding event detected for venue=BINANCE, eventId=F1"
            )),
            alerts.raisedAlerts()
        );
    }

    private PositionState position(EngineState engineState, PositionKey key) {
        return engineState.positions().get(key);
    }

    private FundingEvent funding(String id, Instant timestamp, String amount) {
        return funding(POSITION_KEY.venue(), id, timestamp, amount);
    }

    private FundingEvent funding(
        String venue,
        String id,
        Instant timestamp,
        String amount
    ) {
        return new FundingEvent(
            timestamp,
            id,
            POSITION_KEY.trader(),
            venue,
            POSITION_KEY.venueAccount(),
            POSITION_KEY.symbol(),
            "USDT",
            new BigDecimal(amount)
        );
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros());
    }

    private record RaisedAlert(AlertSeverity severity, String message) {}

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
