package com.coalesce.challenge.io;

import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.event.TradeEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeCsvLoaderTest {
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-08-02T00:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void GIVEN_canonicalColumns_WHEN_loaded_THEN_mapsTrade() throws IOException {
        Path file = temporaryDirectory.resolve("trades.csv");
        Files.writeString(file, """
            timestamp,venue,trade_id,trader,venue_account,instrument_type,symbol,side,quantity,price,fee,fee_asset
            2026-08-01T01:00:00Z,BINANCE,T1,TRADER-A,ACCOUNT-1,FUTURES,BTCUSDT,BUY,0.25,60000,0.01,BNB
            """);

        TradeEvent trade = new TradeCsvLoader().load(file, START, END).getFirst();

        assertTrade(trade);
    }

    @Test
    void GIVEN_tradesAtWindowBoundaries_WHEN_loaded_THEN_usesHalfOpenWindow() throws IOException {
        Path file = temporaryDirectory.resolve("trades.csv");
        Files.writeString(file, """
            timestamp,venue,trade_id,trader,venue_account,instrument_type,symbol,side,quantity,price,fee,fee_asset
            2026-07-31T23:59:59Z,BINANCE,T0,TRADER,ACCOUNT,FUTURES,BTCUSDT,BUY,1,100,0,USDT
            2026-08-01T00:00:00Z,BINANCE,T1,TRADER,ACCOUNT,FUTURES,BTCUSDT,BUY,1,100,0,USDT
            2026-08-02T00:00:00Z,BINANCE,T2,TRADER,ACCOUNT,FUTURES,BTCUSDT,BUY,1,100,0,USDT
            """);

        List<TradeEvent> trades = new TradeCsvLoader().load(file, START, END);

        assertEquals(1, trades.size());
        assertEquals("T1", trades.getFirst().tradeId());
    }

    @Test
    void GIVEN_reorderedColumns_WHEN_loaded_THEN_mapsTradeByColumnName() throws IOException {
        Path file = temporaryDirectory.resolve("reordered-trades.csv");
        Files.writeString(file, """
            fee_asset,fee,price,quantity,side,symbol,instrument_type,venue_account,trader,trade_id,venue,timestamp
            BNB,0.01,60000,0.25,BUY,BTCUSDT,FUTURES,ACCOUNT-1,TRADER-A,T1,BINANCE,2026-08-01T01:00:00Z
            """);

        TradeEvent trade = new TradeCsvLoader().load(file, START, END).getFirst();

        assertTrade(trade);
    }

    private void assertTrade(TradeEvent trade) {
        assertEquals(Instant.parse("2026-08-01T01:00:00Z"), trade.timestamp());
        assertEquals("BINANCE", trade.venue());
        assertEquals("T1", trade.tradeId());
        assertEquals("TRADER-A", trade.trader());
        assertEquals("ACCOUNT-1", trade.venueAccount());
        assertEquals("BTCUSDT", trade.symbol());
        assertEquals(Side.BUY, trade.side());
        assertDecimal("0.25", trade.quantity());
        assertDecimal("60000", trade.price());
        assertDecimal("0.01", trade.fee());
        assertEquals("BNB", trade.feeAsset());
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros());
    }
}
