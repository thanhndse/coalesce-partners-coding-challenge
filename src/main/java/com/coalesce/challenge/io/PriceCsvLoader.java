package com.coalesce.challenge.io;

import com.coalesce.challenge.event.PriceEvent;
import org.apache.commons.csv.CSVRecord;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Loads price events from their CSV schema. */
public final class PriceCsvLoader {
    private static final String TIMESTAMP = "timestamp";
    private static final String SYMBOL = "symbol";
    private static final String PRICE = "price";

    private static final List<String> COLUMNS = List.of(TIMESTAMP, SYMBOL, PRICE);

    public List<PriceEvent> load(Path path, Instant valuationTimestamp) {
        List<PriceEvent> prices = new ArrayList<>();
        for (CsvFileReader.Row row : CsvFileReader.readRows(path, COLUMNS)) {
            try {
                CSVRecord record = row.record();
                Instant timestamp = Instant.parse(record.get(TIMESTAMP));
                if (timestamp.isAfter(valuationTimestamp)) {
                    continue;
                }
                prices.add(new PriceEvent(
                    timestamp,
                    record.get(SYMBOL),
                    new BigDecimal(record.get(PRICE))
                ));
            } catch (RuntimeException exception) {
                throw CsvFileReader.invalidRow(path, row, exception);
            }
        }
        return List.copyOf(prices);
    }
}
