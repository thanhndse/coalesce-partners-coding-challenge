package com.coalesce.challenge.handler;

import com.coalesce.challenge.event.Event;

public interface EventHandler<T extends Event> {
    Class<T> eventType();

    void handle(T event);

    default void handleEvent(Event event) {
        handle(eventType().cast(event));
    }
}
