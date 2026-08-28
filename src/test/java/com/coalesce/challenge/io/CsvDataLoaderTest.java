package com.coalesce.challenge.io;

import com.coalesce.challenge.event.PriceEvent;
import com.coalesce.challenge.event.TradeEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvDataLoaderTest {
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-08-02T00:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void tradeWindowIsHalfOpen() throws IOException {
        Path file = temporaryDirectory.resolve("trades.csv");
        Files.writeString(file, """
            timestamp,venue,trade_id,trader,venue_account,instrument_type,symbol,side,quantity,price,fee,fee_asset
            2026-07-31T23:59:59Z,BINANCE,T0,TRADER,ACCOUNT,FUTURES,BTCUSDT,BUY,1,100,0,USDT
            2026-08-01T00:00:00Z,BINANCE,T1,TRADER,ACCOUNT,FUTURES,BTCUSDT,BUY,1,100,0,USDT
            2026-08-02T00:00:00Z,BINANCE,T2,TRADER,ACCOUNT,FUTURES,BTCUSDT,BUY,1,100,0,USDT
            """);

        List<TradeEvent> trades = new CsvDataLoader().loadTrades(file, START, END);

        assertEquals(1, trades.size());
        assertEquals("T1", trades.getFirst().tradeId());
    }

    @Test
    void pricesBeforeTheWindowAndAtTheClosingTimestampRemainEligible() throws IOException {
        Path file = temporaryDirectory.resolve("prices.csv");
        Files.writeString(file, """
            timestamp,symbol,price
            2026-07-31T23:00:00Z,BTCUSDT,99
            2026-08-02T00:00:00Z,BTCUSDT,101
            2026-08-02T00:00:01Z,BTCUSDT,102
            """);

        List<PriceEvent> prices = new CsvDataLoader().loadPrices(file, END);

        assertEquals(2, prices.size());
        assertEquals(Instant.parse("2026-07-31T23:00:00Z"), prices.get(0).timestamp());
        assertEquals(END, prices.get(1).timestamp());
    }
}
