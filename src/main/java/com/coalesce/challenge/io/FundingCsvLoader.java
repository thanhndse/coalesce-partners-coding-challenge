package com.coalesce.challenge.io;

import com.coalesce.challenge.event.FundingEvent;
import org.apache.commons.csv.CSVRecord;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Loads funding events from their CSV schema. */
public final class FundingCsvLoader {
    private static final String TIMESTAMP = "timestamp";
    private static final String EVENT_ID = "event_id";
    private static final String TRADER = "trader";
    private static final String VENUE = "venue";
    private static final String VENUE_ACCOUNT = "venue_account";
    private static final String SYMBOL = "symbol";
    private static final String ASSET = "asset";
    private static final String AMOUNT = "amount";

    private static final List<String> COLUMNS = List.of(
        TIMESTAMP, EVENT_ID, TRADER, VENUE, VENUE_ACCOUNT, SYMBOL, ASSET, AMOUNT
    );

    public List<FundingEvent> load(Path path, Instant periodStart, Instant periodEnd) {
        List<FundingEvent> fundingEvents = new ArrayList<>();
        for (CsvFileReader.Row row : CsvFileReader.readRows(path, COLUMNS)) {
            try {
                CSVRecord record = row.record();
                Instant timestamp = Instant.parse(record.get(TIMESTAMP));
                if (timestamp.isBefore(periodStart) || !timestamp.isBefore(periodEnd)) {
                    continue;
                }
                FundingEvent fundingEvent = new FundingEvent(
                    timestamp,
                    record.get(EVENT_ID),
                    record.get(TRADER),
                    record.get(VENUE),
                    record.get(VENUE_ACCOUNT),
                    record.get(SYMBOL),
                    record.get(ASSET),
                    new BigDecimal(record.get(AMOUNT))
                );
                fundingEvents.add(fundingEvent);
            } catch (RuntimeException exception) {
                throw CsvFileReader.invalidRow(path, row, exception);
            }
        }
        return List.copyOf(fundingEvents);
    }
}
