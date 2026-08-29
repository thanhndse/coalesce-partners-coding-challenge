package com.coalesce.challenge.engine;

import com.coalesce.challenge.domain.PositionKey;
import com.coalesce.challenge.event.FundingEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PositionEventHistoryTest {
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");
    private static final PositionKey POSITION_KEY =
            new PositionKey("TRADER", "BINANCE", "ACCOUNT", "BTCUSDT");

    @Test
    void GIVEN_knownEventCapacity_WHEN_exceeded_THEN_evictsOldestIdentity() {
        PositionEventHistory history = new PositionEventHistory(10, 2);
        PositionState state = PositionState.empty();
        PriceBook priceBook = new PriceBook();
        FundingEvent first = funding("F1", 1);
        FundingEvent second = funding("F2", 2);
        FundingEvent third = funding("F3", 3);

        history.retainFunding(POSITION_KEY, state, first, priceBook);
        history.retainFunding(POSITION_KEY, state, second, priceBook);
        history.retainFunding(POSITION_KEY, state, third, priceBook);

        assertNull(history.knownEvent(first.identity()));
        assertSame(second, history.knownEvent(second.identity()));
        assertSame(third, history.knownEvent(third.identity()));
    }

    private FundingEvent funding(String id, int secondsAfterStart) {
        return new FundingEvent(
                START.plusSeconds(secondsAfterStart),
                id,
                POSITION_KEY.trader(),
                POSITION_KEY.venue(),
                POSITION_KEY.venueAccount(),
                POSITION_KEY.symbol(),
                "USDT",
                BigDecimal.ONE
        );
    }
}
