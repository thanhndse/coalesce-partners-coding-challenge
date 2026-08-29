package com.coalesce.challenge;

import com.coalesce.challenge.domain.OpeningPosition;
import com.coalesce.challenge.domain.PositionKey;
import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.engine.PnlEngine;
import com.coalesce.challenge.event.TradeEvent;
import com.coalesce.challenge.state.EngineState;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ApplicationModuleTest {
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void WHEN_engineGraphInjected_THEN_sharesOneEngineState() {
        Injector injector = Guice.createInjector(new ApplicationModule());
        EngineState engineState = injector.getInstance(EngineState.class);
        PnlEngine engine = injector.getInstance(PnlEngine.class);
        PositionKey key = new PositionKey("TRADER", "VENUE", "ACCOUNT", "BTCUSDT");

        engine.initialize(List.of(new OpeningPosition(
            START, key.trader(), key.venue(), key.venueAccount(), key.symbol(),
            BigDecimal.ONE, new BigDecimal("100")
        )));
        engine.process(new TradeEvent(
            START.plusSeconds(1), key.venue(), "T1", key.trader(), key.venueAccount(),
            key.symbol(), Side.BUY, BigDecimal.ONE, new BigDecimal("110"),
            BigDecimal.ZERO, "USDT"
        ));

        assertSame(engineState, injector.getInstance(EngineState.class));
        assertSame(engine, injector.getInstance(PnlEngine.class));
        assertEquals(new BigDecimal("2"), engineState.positions().get(key).quantity());
    }
}
