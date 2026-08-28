package com.coalesce.challenge.event;

public enum EventType {
    PRICE(0),
    TRADE(1),
    FUNDING(2);

    private final int priority;

    EventType(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
