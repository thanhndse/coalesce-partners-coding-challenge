package com.coalesce.challenge.io;

import com.coalesce.challenge.domain.OpeningPosition;
import org.apache.commons.csv.CSVRecord;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class OpeningPositionCsvLoader {
    private static final String TIMESTAMP = "timestamp";
    private static final String TRADER = "trader";
    private static final String VENUE = "venue";
    private static final String VENUE_ACCOUNT = "venue_account";
    private static final String SYMBOL = "symbol";
    private static final String QUANTITY = "quantity";
    private static final String AVERAGE_ENTRY_PRICE = "avg_entry_price";

    private static final List<String> COLUMNS = List.of(
        TIMESTAMP, TRADER, VENUE, VENUE_ACCOUNT, SYMBOL, QUANTITY, AVERAGE_ENTRY_PRICE
    );

    public List<OpeningPosition> load(Path path) {
        List<OpeningPosition> positions = new ArrayList<>();
        for (CsvFileReader.Row row : CsvFileReader.readRows(path, COLUMNS)) {
            try {
                CSVRecord record = row.record();
                positions.add(new OpeningPosition(
                    Instant.parse(record.get(TIMESTAMP)),
                    record.get(TRADER),
                    record.get(VENUE),
                    record.get(VENUE_ACCOUNT),
                    record.get(SYMBOL),
                    new BigDecimal(record.get(QUANTITY)),
                    new BigDecimal(record.get(AVERAGE_ENTRY_PRICE))
                ));
            } catch (RuntimeException exception) {
                throw CsvFileReader.invalidRow(path, row, exception);
            }
        }
        return List.copyOf(positions);
    }
}
