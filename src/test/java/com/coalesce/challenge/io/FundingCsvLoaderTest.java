package com.coalesce.challenge.io;

import com.coalesce.challenge.event.FundingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FundingCsvLoaderTest {
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-08-02T00:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void GIVEN_reorderedFundingColumns_WHEN_loaded_THEN_mapsNamesAndFiltersWindow() throws IOException {
        Path file = temporaryDirectory.resolve("funding.csv");
        Files.writeString(file, """
            amount,asset,symbol,venue_account,venue,trader,event_id,timestamp
            -1.00,USDT,BTCUSDT,ACCOUNT-0,BINANCE,TRADER-A,F0,2026-07-31T23:59:59Z
            -2.50,USDT,BTCUSDT,ACCOUNT-1,BINANCE,TRADER-A,F1,2026-08-01T00:00:00Z
            3.25,USDT,ETHUSDT,ACCOUNT-2,OKX,TRADER-B,F2,2026-08-01T12:00:00Z
            4.00,USDT,BTCUSDT,ACCOUNT-3,BINANCE,TRADER-C,F3,2026-08-02T00:00:00Z
            """);

        List<FundingEvent> funding = new FundingCsvLoader().load(file, START, END);

        assertEquals(2, funding.size());

        FundingEvent first = funding.getFirst();
        assertEquals(START, first.timestamp());
        assertEquals("F1", first.eventId());
        assertEquals("TRADER-A", first.trader());
        assertEquals("BINANCE", first.venue());
        assertEquals("ACCOUNT-1", first.venueAccount());
        assertEquals("BTCUSDT", first.symbol());
        assertEquals("USDT", first.asset());
        assertEquals(new BigDecimal("-2.5"), first.amount());

        assertEquals("F2", funding.get(1).eventId());
    }

}
