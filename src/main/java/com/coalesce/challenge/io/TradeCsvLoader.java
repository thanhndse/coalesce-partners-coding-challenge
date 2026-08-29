package com.coalesce.challenge.io;

import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.event.TradeEvent;
import org.apache.commons.csv.CSVRecord;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Loads trade events from their CSV schema. */
public final class TradeCsvLoader {
    private static final String TIMESTAMP = "timestamp";
    private static final String VENUE = "venue";
    private static final String TRADE_ID = "trade_id";
    private static final String TRADER = "trader";
    private static final String VENUE_ACCOUNT = "venue_account";
    private static final String INSTRUMENT_TYPE = "instrument_type";
    private static final String SYMBOL = "symbol";
    private static final String SIDE = "side";
    private static final String QUANTITY = "quantity";
    private static final String PRICE = "price";
    private static final String FEE = "fee";
    private static final String FEE_ASSET = "fee_asset";

    private static final List<String> COLUMNS = List.of(
        TIMESTAMP, VENUE, TRADE_ID, TRADER, VENUE_ACCOUNT, INSTRUMENT_TYPE, SYMBOL, SIDE,
        QUANTITY, PRICE, FEE, FEE_ASSET
    );

    public List<TradeEvent> load(Path path, Instant periodStart, Instant periodEnd) {
        List<TradeEvent> trades = new ArrayList<>();
        for (CsvFileReader.Row row : CsvFileReader.readRows(path, COLUMNS)) {
            try {
                CSVRecord record = row.record();
                Instant timestamp = Instant.parse(record.get(TIMESTAMP));
                if (timestamp.isBefore(periodStart) || !timestamp.isBefore(periodEnd)) {
                    continue;
                }
                TradeEvent trade = new TradeEvent(
                    timestamp,
                    record.get(VENUE),
                    record.get(TRADE_ID),
                    record.get(TRADER),
                    record.get(VENUE_ACCOUNT),
                    record.get(SYMBOL),
                    Side.valueOf(record.get(SIDE)),
                    new BigDecimal(record.get(QUANTITY)),
                    new BigDecimal(record.get(PRICE)),
                    new BigDecimal(record.get(FEE)),
                    record.get(FEE_ASSET)
                );
                trades.add(trade);
            } catch (RuntimeException exception) {
                throw CsvFileReader.invalidRow(path, row, exception);
            }
        }
        return List.copyOf(trades);
    }
}
