package com.coalesce.challenge.handler;

import com.coalesce.challenge.alert.AlertManager;
import com.coalesce.challenge.alert.AlertSeverity;
import com.coalesce.challenge.engine.*;
import com.coalesce.challenge.event.Event;
import com.coalesce.challenge.event.TradeEvent;
import com.coalesce.challenge.state.EngineState;
import com.google.inject.Inject;

import java.time.Instant;
import java.util.Objects;

public final class TradeEventHandler implements EventHandler<TradeEvent> {
    private final EngineState engineState;
    private final PositionReplayService replayService;
    private final AlertManager alertManager;

    @Inject
    public TradeEventHandler(
            EngineState engineState,
            PositionReplayService replayService,
            AlertManager alertManager
    ) {
        this.engineState = Objects.requireNonNull(engineState, "engineState");
        this.replayService = Objects.requireNonNull(replayService, "replayService");
        this.alertManager = Objects.requireNonNull(alertManager, "alertManager");
    }


    @Override
    public Class<TradeEvent> eventType() {
        return TradeEvent.class;
    }

    @Override
    public void handle(TradeEvent trade) {
        if (alertIfAlreadyKnown(trade)) {
            return;
        }

        PositionState state = engineState.positions().computeIfAbsent(
                trade.positionKey(), ignored -> PositionState.empty()
        );
        RetentionResult retention =
                engineState.positionEventHistory().retainTrade(
                        trade.positionKey(), state, trade, engineState.priceBook()
                );
        if (!retention.accepted()) {
            alertLateTradeRejected(trade, retention.replayBoundary());
            return;
        }

        if (retention.late()) {
            replayService.replay(trade.positionKey(), retention);
            alertLateTradeReplayed(trade);
        } else {
            PositionCalculator.applyTrade(
                    state, trade, engineState.priceBook()
            );
        }
    }

    private boolean alertIfAlreadyKnown(TradeEvent trade) {
        Event knownEvent = engineState.positionEventHistory().knownEvent(
                trade.identity()
        );
        if (knownEvent == null){
            return false;
        }
        TradeEvent knownTrade = (TradeEvent) knownEvent;
        if (hasSamePayload(knownTrade, trade)) {
            alertManager.raise(
                    AlertSeverity.NEED_TO_BE_NOTIFIED,
                    "Duplicate trade detected for venue=" + trade.venue()
                            + ", tradeId=" + trade.tradeId()
            );
            return true;
        }

        alertManager.raise(
                AlertSeverity.REQUIRES_IMMEDIATE_ACTION,
                "Conflicting trade payload detected for venue=" + trade.venue()
                        + ", tradeId=" + trade.tradeId()
        );
        return true;
    }

    private boolean hasSamePayload(TradeEvent retained, TradeEvent received) {
        return retained.timestamp().equals(received.timestamp())
                && retained.trader().equals(received.trader())
                && retained.venueAccount().equals(received.venueAccount())
                && retained.symbol().equals(received.symbol())
                && retained.side() == received.side()
                && retained.quantity().compareTo(received.quantity()) == 0
                && retained.price().compareTo(received.price()) == 0
                && retained.fee().compareTo(received.fee()) == 0
                && retained.feeAsset().equals(received.feeAsset());
    }

    private void alertLateTradeRejected(TradeEvent trade, Instant boundary) {
        alertManager.raise(
                AlertSeverity.REQUIRES_IMMEDIATE_ACTION,
                "Late trade is at or older than the replay boundary for trader="
                        + trade.trader()
                        + ", venue=" + trade.venue()
                        + ", venueAccount=" + trade.venueAccount()
                        + ", symbol=" + trade.symbol()
                        + ", tradeId=" + trade.tradeId()
                        + ", timestamp=" + trade.timestamp()
                        + ", replayBoundary=" + boundary
        );
    }

    private void alertLateTradeReplayed(TradeEvent trade) {
        alertManager.raise(
                AlertSeverity.SELF_RECOVERABLE,
                "Late trade replayed for trader=" + trade.trader()
                        + ", venue=" + trade.venue()
                        + ", venueAccount=" + trade.venueAccount()
                        + ", symbol=" + trade.symbol()
                        + ", tradeId=" + trade.tradeId()
                        + ", timestamp=" + trade.timestamp()
        );
    }

}
