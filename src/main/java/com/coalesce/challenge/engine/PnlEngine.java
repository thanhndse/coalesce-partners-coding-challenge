package com.coalesce.challenge.engine;

import com.coalesce.challenge.domain.OpeningPosition;
import com.coalesce.challenge.event.Event;
import com.coalesce.challenge.handler.EventHandler;
import com.coalesce.challenge.state.EngineState;
import com.google.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory incremental PnL engine.
 */
public final class PnlEngine {
    private final EngineState engineState;
    private final Map<Class<? extends Event>, EventHandler<?>> handlersByEventType;

    @Inject
    public PnlEngine(EngineState engineState, Set<EventHandler<?>> eventHandlers) {
        this.engineState = engineState;
        this.handlersByEventType = createHandlerRegistry(eventHandlers);
    }

    public void initialize(List<OpeningPosition> openings) {
        if (engineState.processingStarted()) {
            throw new IllegalStateException("Opening positions must be loaded before events");
        }

        for (OpeningPosition opening : openings) {
            PositionState previous = engineState.positions().putIfAbsent(
                    opening.key(), PositionState.from(opening)
            );
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate opening position for " + opening.key()
                );
            }
        }
    }

    public void process(Event event) {
        EventHandler<?> handler = handlersByEventType.get(event.getClass());
        if (handler == null) {
            throw new IllegalArgumentException(
                    "No handler registered for " + event.getClass().getName()
            );
        }
        handler.handleEvent(event);
        engineState.markProcessingStarted();
    }

    private static Map<Class<? extends Event>, EventHandler<?>> createHandlerRegistry(
            Set<EventHandler<?>> eventHandlers
    ) {
        Map<Class<? extends Event>, EventHandler<?>> handlers = new HashMap<>();
        for (EventHandler<?> handler : eventHandlers) {
            EventHandler<?> previous = handlers.put(handler.eventType(), handler);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Multiple handlers registered for " + handler.eventType().getName()
                );
            }
        }
        return Map.copyOf(handlers);
    }
}
