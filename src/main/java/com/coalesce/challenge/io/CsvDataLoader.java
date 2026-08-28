package com.coalesce.challenge.io;

import com.coalesce.challenge.domain.OpeningPosition;
import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.event.FundingEvent;
import com.coalesce.challenge.event.PriceEvent;
import com.coalesce.challenge.event.TradeEvent;
import com.coalesce.challenge.exception.CsvDataException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Loader for the challenge's simple, unquoted CSV schema. */
public final class CsvDataLoader {
    private static final String OPENING_HEADER =
        "timestamp,trader,venue,venue_account,symbol,quantity,avg_entry_price";
    private static final String TRADE_HEADER =
        "timestamp,venue,trade_id,trader,venue_account,instrument_type,symbol,side,quantity,price,fee,fee_asset";
    private static final String FUNDING_HEADER =
        "timestamp,event_id,trader,venue,venue_account,symbol,asset,amount";
    private static final String PRICE_HEADER = "timestamp,symbol,price";

    public List<OpeningPosition> loadOpeningPositions(Path path) {
        List<OpeningPosition> positions = new ArrayList<>();
        for (CsvRow row : readRows(path, OPENING_HEADER, 7)) {
            try {
                String[] fields = row.fields();
                positions.add(new OpeningPosition(
                    Instant.parse(fields[0]),
                    fields[1],
                    fields[2],
                    fields[3],
                    fields[4],
                    new BigDecimal(fields[5]),
                    new BigDecimal(fields[6])
                ));
            } catch (RuntimeException exception) {
                throw invalidRow(path, row, exception);
            }
        }
        return List.copyOf(positions);
    }

    public List<TradeEvent> loadTrades(Path path, Instant periodStart, Instant periodEnd) {
        List<TradeEvent> trades = new ArrayList<>();
        for (CsvRow row : readRows(path, TRADE_HEADER, 12)) {
            try {
                String[] fields = row.fields();
                Instant timestamp = Instant.parse(fields[0]);
                if (timestamp.isBefore(periodStart) || !timestamp.isBefore(periodEnd)) {
                    continue;
                }
                if (!fields[5].equals("FUTURES")) {
                    throw new IllegalArgumentException(
                        "Unsupported instrument_type " + fields[5]
                    );
                }
                trades.add(new TradeEvent(
                    timestamp,
                    fields[1],
                    fields[2],
                    fields[3],
                    fields[4],
                    fields[6],
                    Side.valueOf(fields[7]),
                    new BigDecimal(fields[8]),
                    new BigDecimal(fields[9]),
                    new BigDecimal(fields[10]),
                    fields[11]
                ));
            } catch (RuntimeException exception) {
                throw invalidRow(path, row, exception);
            }
        }
        return List.copyOf(trades);
    }

    public List<FundingEvent> loadFunding(Path path, Instant periodStart, Instant periodEnd) {
        List<FundingEvent> fundingEvents = new ArrayList<>();
        for (CsvRow row : readRows(path, FUNDING_HEADER, 8)) {
            try {
                String[] fields = row.fields();
                Instant timestamp = Instant.parse(fields[0]);
                if (timestamp.isBefore(periodStart) || !timestamp.isBefore(periodEnd)) {
                    continue;
                }
                fundingEvents.add(new FundingEvent(
                    timestamp,
                    fields[1],
                    fields[2],
                    fields[3],
                    fields[4],
                    fields[5],
                    fields[6],
                    new BigDecimal(fields[7])
                ));
            } catch (RuntimeException exception) {
                throw invalidRow(path, row, exception);
            }
        }
        return List.copyOf(fundingEvents);
    }

    public List<PriceEvent> loadPrices(Path path, Instant valuationTimestamp) {
        List<PriceEvent> prices = new ArrayList<>();
        for (CsvRow row : readRows(path, PRICE_HEADER, 3)) {
            try {
                String[] fields = row.fields();
                Instant timestamp = Instant.parse(fields[0]);
                if (timestamp.isAfter(valuationTimestamp)) {
                    continue;
                }
                prices.add(new PriceEvent(
                    timestamp,
                    fields[1],
                    new BigDecimal(fields[2])
                ));
            } catch (RuntimeException exception) {
                throw invalidRow(path, row, exception);
            }
        }
        return List.copyOf(prices);
    }

    private List<CsvRow> readRows(Path path, String expectedHeader, int expectedColumns) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                throw new CsvDataException(path, 1, "CSV file is empty");
            }
            header = stripCarriageReturn(stripBom(header));
            if (!header.equals(expectedHeader)) {
                throw new CsvDataException(path, 1, "Unexpected header: " + header);
            }

            List<CsvRow> rows = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = stripCarriageReturn(line);
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split(",", -1);
                if (fields.length != expectedColumns) {
                    throw new CsvDataException(
                        path,
                        lineNumber,
                        "Expected " + expectedColumns + " columns, found " + fields.length
                    );
                }
                for (String field : fields) {
                    if (field.isEmpty()) {
                        throw new CsvDataException(path, lineNumber, "Empty fields are not supported");
                    }
                }
                rows.add(new CsvRow(lineNumber, fields));
            }
            return rows;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read " + path, exception);
        }
    }

    private CsvDataException invalidRow(Path path, CsvRow row, RuntimeException exception) {
        if (exception instanceof CsvDataException csvDataException) {
            return csvDataException;
        }
        return new CsvDataException(path, row.lineNumber(), exception.getMessage(), exception);
    }

    private String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private String stripCarriageReturn(String value) {
        return value.endsWith("\r") ? value.substring(0, value.length() - 1) : value;
    }

    private record CsvRow(int lineNumber, String[] fields) {
    }
}
