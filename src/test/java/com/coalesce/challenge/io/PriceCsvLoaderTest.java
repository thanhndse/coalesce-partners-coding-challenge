package com.coalesce.challenge.io;

import com.coalesce.challenge.event.PriceEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceCsvLoaderTest {
    private static final Instant END = Instant.parse("2026-08-02T00:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void GIVEN_pricesAroundWindow_WHEN_loaded_THEN_keepsEligibleReferencePrices() throws IOException {
        Path file = temporaryDirectory.resolve("prices.csv");
        Files.writeString(file, """
            timestamp,symbol,price
            2026-07-31T23:00:00Z,BTCUSDT,99
            2026-08-02T00:00:00Z,BTCUSDT,101
            2026-08-02T00:00:01Z,BTCUSDT,102
            """);

        List<PriceEvent> prices = new PriceCsvLoader().load(file, END);

        assertEquals(2, prices.size());
        assertEquals(Instant.parse("2026-07-31T23:00:00Z"), prices.get(0).timestamp());
        assertEquals(END, prices.get(1).timestamp());
    }

    @Test
    void GIVEN_reorderedColumns_WHEN_loaded_THEN_mapsPriceByColumnName() throws IOException {
        Path file = temporaryDirectory.resolve("reordered-prices.csv");
        Files.writeString(file, """
            price,symbol,timestamp
            101.25,BTCUSDT,2026-08-01T12:00:00Z
            """);

        PriceEvent price = new PriceCsvLoader().load(file, END).getFirst();

        assertEquals("BTCUSDT", price.symbol());
        assertEquals(Instant.parse("2026-08-01T12:00:00Z"), price.timestamp());
        assertEquals(0, new BigDecimal("101.25").compareTo(price.price()));
    }
}
