package com.coalesce.challenge.engine;

import com.coalesce.challenge.event.PriceEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceBookTest {
    @Test
    void returnsLatestPriceAtOrBeforeTimestampWithoutUsingTheFuture() {
        PriceBook priceBook = new PriceBook();
        priceBook.add(price("10:00:00Z", "800"));
        priceBook.add(price("10:05:00Z", "805"));

        assertTrue(priceBook.latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T09:59:59Z")
        ).isEmpty());
        assertDecimal("800", priceBook.latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T10:00:00Z")
        ).orElseThrow());
        assertDecimal("800", priceBook.latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T10:03:00Z")
        ).orElseThrow());
        assertDecimal("805", priceBook.latestPrice(
            "BNBUSDT", Instant.parse("2026-08-01T10:05:00Z")
        ).orElseThrow());
    }

    private PriceEvent price(String time, String price) {
        return new PriceEvent(
            Instant.parse("2026-08-01T" + time), "BNBUSDT", new BigDecimal(price)
        );
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
