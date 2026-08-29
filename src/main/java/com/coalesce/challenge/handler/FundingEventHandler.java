package com.coalesce.challenge.handler;

import com.coalesce.challenge.alert.AlertManager;
import com.coalesce.challenge.alert.AlertSeverity;
import com.coalesce.challenge.engine.PositionCalculator;
import com.coalesce.challenge.engine.PositionState;
import com.coalesce.challenge.event.Event;
import com.coalesce.challenge.event.FundingEvent;
import com.coalesce.challenge.state.EngineState;
import com.google.inject.Inject;

import java.util.Objects;

public final class FundingEventHandler implements EventHandler<FundingEvent> {
    private static final String REPORTING_ASSET = "USDT";

    private final EngineState engineState;
    private final AlertManager alertManager;

    @Inject
    public FundingEventHandler(
        EngineState engineState,
        AlertManager alertManager
    ) {
        this.engineState = Objects.requireNonNull(engineState, "engineState");
        this.alertManager = Objects.requireNonNull(alertManager, "alertManager");
    }

    @Override
    public Class<FundingEvent> eventType() {
        return FundingEvent.class;
    }

    @Override
    public void handle(FundingEvent funding) {
        if (!funding.asset().equals(REPORTING_ASSET)) {
            throw new IllegalArgumentException(
                "Only USDT funding is supported, received " + funding.asset()
            );
        }

        if (alertIfAlreadyKnown(funding)) {
            return;
        }

        PositionState state = engineState.positions().computeIfAbsent(
            funding.positionKey(), ignored -> PositionState.empty()
        );
        engineState.positionEventHistory().retainFunding(
            funding.positionKey(), state, funding, engineState.priceBook()
        );
        PositionCalculator.applyFunding(state, funding);
    }

    private boolean alertIfAlreadyKnown(FundingEvent funding) {
        Event knownEvent = engineState.positionEventHistory().knownEvent(
            funding.identity()
        );
        if (!(knownEvent instanceof FundingEvent knownFunding)) {
            return false;
        }

        if (hasSamePayload(knownFunding, funding)) {
            alertManager.raise(
                AlertSeverity.NEED_TO_BE_NOTIFIED,
                "Duplicate funding event detected for venue=" + funding.venue()
                    + ", eventId=" + funding.eventId()
            );
            return true;
        }

        alertManager.raise(
            AlertSeverity.REQUIRES_IMMEDIATE_ACTION,
            "Conflicting funding event payload detected for venue=" + funding.venue()
                + ", eventId=" + funding.eventId()
        );
        return true;
    }

    private boolean hasSamePayload(FundingEvent retained, FundingEvent received) {
        return retained.timestamp().equals(received.timestamp())
            && retained.trader().equals(received.trader())
            && retained.venueAccount().equals(received.venueAccount())
            && retained.symbol().equals(received.symbol())
            && retained.asset().equals(received.asset())
            && retained.amount().compareTo(received.amount()) == 0;
    }

}
