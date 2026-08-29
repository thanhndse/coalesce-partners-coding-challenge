package com.coalesce.challenge.engine;

import com.coalesce.challenge.domain.PositionKey;
import com.coalesce.challenge.event.Event;
import com.coalesce.challenge.event.FundingEvent;
import com.coalesce.challenge.event.PriceEvent;
import com.coalesce.challenge.event.TradeEvent;
import com.coalesce.challenge.state.EngineState;
import com.google.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Rebuilds one position from its checkpoint and retained event window. */
public final class PositionReplayService {
    private final EngineState engineState;

    @Inject
    public PositionReplayService(EngineState engineState) {
        this.engineState = Objects.requireNonNull(engineState, "engineState");
    }

    public void replay(
        PositionKey key,
        RetentionResult retention
    ) {
        if (!retention.accepted() || !retention.late()) {
            throw new IllegalArgumentException(
                "Replay requires an accepted late event"
            );
        }

        replay(key, retention.checkpoint(), retention.retainedEvents());
    }

    private void replay(
            PositionKey key,
            PositionState checkpoint,
            Iterable<Event> events
    ) {
        PositionState replayedState = checkpoint.copy();
        for (Event event : events) {
            apply(replayedState, event);
        }
        engineState.positions().put(key, replayedState);
    }

    private void apply(PositionState state, Event event) {
        switch (event) {
            case TradeEvent trade -> PositionCalculator.applyTrade(
                    state, trade, engineState.priceBook()
            );
            case FundingEvent funding -> PositionCalculator.applyFunding(
                    state, funding
            );
            case PriceEvent price -> throw new IllegalArgumentException(
                    "Price event cannot be replayed as a position event: "
                            + price.identity()
            );
        }
    }
}
