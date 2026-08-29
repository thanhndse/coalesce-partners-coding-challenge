package com.coalesce.challenge.handler;

import com.coalesce.challenge.alert.AlertManager;
import com.coalesce.challenge.alert.AlertSeverity;
import com.coalesce.challenge.engine.PositionEventHistory;
import com.coalesce.challenge.engine.PositionState;
import com.coalesce.challenge.engine.PriceBook;
import com.coalesce.challenge.engine.ReportingFeeCalculator;
import com.coalesce.challenge.event.PriceEvent;
import com.coalesce.challenge.event.TradeEvent;
import com.coalesce.challenge.state.EngineState;
import com.google.inject.Inject;

import java.math.BigDecimal;
import java.util.Objects;

public final class PriceEventHandler implements EventHandler<PriceEvent> {
    private final EngineState engineState;
    private final AlertManager alertManager;

    @Inject
    public PriceEventHandler(
            EngineState engineState,
            AlertManager alertManager
    ) {
        this.engineState = Objects.requireNonNull(engineState, "engineState");
        this.alertManager = Objects.requireNonNull(alertManager, "alertManager");
    }

    @Override
    public Class<PriceEvent> eventType() {
        return PriceEvent.class;
    }

    @Override
    public void handle(PriceEvent price) {
        PriceBook.PriceUpdate priceUpdate = engineState.priceBook().add(price);
        if (!priceUpdate.retained()) {
            return;
        }

        for (PositionEventHistory.AffectedPositionFees affected : engineState
                .positionEventHistory().affectedFees(priceUpdate)) {
            if (affected.replayBoundary() != null) {
                alertIncompleteCorrection(affected, priceUpdate);
                continue;
            }
            adjustFees(affected, priceUpdate);
        }
    }

    private void adjustFees(
            PositionEventHistory.AffectedPositionFees affected,
            PriceBook.PriceUpdate priceUpdate
    ) {
        PositionState position = Objects.requireNonNull(
                engineState.positions().get(affected.positionKey()),
                "No current position for " + affected.positionKey()
        );
        for (TradeEvent trade : affected.trades()) {
            BigDecimal newFee = ReportingFeeCalculator.calculate(
                    trade, engineState.priceBook()
            ).orElseThrow();
            priceUpdate.previousPrice().ifPresentOrElse(
                    previousPrice -> position.addFee(newFee.subtract(
                            trade.fee().multiply(previousPrice)
                    )),
                    () -> position.resolveFee(newFee)
            );
        }
    }

    private void alertIncompleteCorrection(
            PositionEventHistory.AffectedPositionFees affected,
            PriceBook.PriceUpdate priceUpdate
    ) {
        alertManager.raise(
                AlertSeverity.REQUIRES_IMMEDIATE_ACTION,
                "Late price may affect fees outside the retained event window for "
                        + "trader=" + affected.positionKey().trader()
                        + ", venue=" + affected.positionKey().venue()
                        + ", venueAccount="
                        + affected.positionKey().venueAccount()
                        + ", symbol=" + affected.positionKey().symbol()
                        + ", conversionSymbol=" + priceUpdate.symbol()
                        + ", timestamp="
                        + priceUpdate.affectedFromInclusive()
                        + ", replayBoundary=" + affected.replayBoundary()
        );
    }
}
