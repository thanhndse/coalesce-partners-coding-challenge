package com.coalesce.challenge.state;

import com.coalesce.challenge.domain.PositionKey;
import com.coalesce.challenge.engine.PositionEventHistory;
import com.coalesce.challenge.engine.PositionState;
import com.coalesce.challenge.engine.PriceBook;
import com.google.inject.Inject;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All mutable in-memory state owned by one PnL engine application instance.
 */
public final class EngineState {
    private final Map<PositionKey, PositionState> positions = new ConcurrentHashMap<>();
    private final PriceBook priceBook;
    private final PositionEventHistory positionEventHistory;
    private boolean processingStarted;

    @Inject
    public EngineState(
            PriceBook priceBook,
            PositionEventHistory positionEventHistory
    ) {
        this.priceBook = Objects.requireNonNull(priceBook, "priceBook");
        this.positionEventHistory = Objects.requireNonNull(
                positionEventHistory, "positionEventHistory"
        );
    }

    public Map<PositionKey, PositionState> positions() {
        return positions;
    }

    public PriceBook priceBook() {
        return priceBook;
    }

    public PositionEventHistory positionEventHistory() {
        return positionEventHistory;
    }

    public boolean processingStarted() {
        return processingStarted;
    }

    public void markProcessingStarted() {
        processingStarted = true;
    }
}
