package com.coalesce.challenge.handler;

import com.coalesce.challenge.engine.PositionEventHistory;
import com.coalesce.challenge.engine.PriceBook;
import com.coalesce.challenge.event.PriceEvent;
import com.coalesce.challenge.state.EngineState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceEventHandlerTest {
    @Test
    void GIVEN_pricesInOrder_WHEN_handled_THEN_returnsLatestPriceAtRequestedTime() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        PriceEventHandler handler = priceHandler(engineState);

        handler.handle(price("10:00:00Z", "800"));
        handler.handle(price("10:05:00Z", "805"));

        assertDecimal("800", engineState.priceBook().latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T10:03:00Z")
        ).orElseThrow());
        assertDecimal("805", engineState.priceBook().latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T10:05:00Z")
        ).orElseThrow());
        assertDecimal("805", engineState.priceBook().latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T10:10:00Z")
        ).orElseThrow());
    }

    @Test
    void GIVEN_priceArrivesLateWithinCapacity_WHEN_handled_THEN_addsItToPriceBook() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        PriceEventHandler handler = priceHandler(engineState);
        handler.handle(price("20:00:00Z", "900"));

        handler.handle(price("10:30:00Z", "850"));

        assertDecimal("850", engineState.priceBook().latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T10:45:00Z")
        ).orElseThrow());
        assertDecimal("900", engineState.priceBook().latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T20:00:00Z")
        ).orElseThrow());
    }

    @Test
    void GIVEN_otherSymbolAdvances_WHEN_bnbPriceArrivesLate_THEN_retainsItIndependently() {
        EngineState engineState = new EngineState(new PriceBook(), new PositionEventHistory());
        PriceEventHandler handler = priceHandler(engineState);
        handler.handle(price("BNBUSDT", "11:00:00Z", "900"));
        handler.handle(price("ETHUSDT", "13:00:00Z", "2000"));

        handler.handle(price("BNBUSDT", "10:30:00Z", "850"));

        assertDecimal("850", engineState.priceBook().latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T10:45:00Z")
        ).orElseThrow());
    }

    @Test
    void GIVEN_configuredCapacity_WHEN_exceeded_THEN_removesOldestPriceForSymbol() {
        EngineState engineState = new EngineState(new PriceBook(2), new PositionEventHistory());
        PriceEventHandler handler = priceHandler(engineState);
        handler.handle(price("10:00:00Z", "800"));
        handler.handle(price("10:01:00Z", "801"));
        handler.handle(price("10:02:00Z", "802"));

        assertTrue(engineState.priceBook().latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T10:00:00Z")
        ).isEmpty());
        assertDecimal("801", engineState.priceBook().latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T10:01:00Z")
        ).orElseThrow());
        assertDecimal("802", engineState.priceBook().latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T10:02:00Z")
        ).orElseThrow());
    }

    private PriceEvent price(String time, String price) {
        return price("BNBUSDT", time, price);
    }

    private PriceEvent price(String symbol, String time, String price) {
        return new PriceEvent(
            Instant.parse("2026-08-01T" + time), symbol, new BigDecimal(price)
        );
    }

    private PriceEventHandler priceHandler(EngineState engineState) {
        return new PriceEventHandler(
                engineState, (severity, message) -> { }
        );
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros());
    }

}
