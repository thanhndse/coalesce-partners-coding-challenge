package com.coalesce.challenge.io;

import com.coalesce.challenge.domain.OpeningPosition;
import com.coalesce.challenge.exception.CsvDataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpeningPositionCsvLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void GIVEN_canonicalColumns_WHEN_loaded_THEN_mapsOpeningPosition() throws IOException {
        Path file = temporaryDirectory.resolve("opening-positions.csv");
        Files.writeString(file, """
            timestamp,trader,venue,venue_account,symbol,quantity,avg_entry_price
            2026-08-01T00:00:00Z,TRADER-A,BINANCE,ACCOUNT-1,BTCUSDT,0.25,60000.50
            """);

        OpeningPosition position = new OpeningPositionCsvLoader().load(file).getFirst();

        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), position.timestamp());
        assertEquals("TRADER-A", position.trader());
        assertEquals("BINANCE", position.venue());
        assertEquals("ACCOUNT-1", position.venueAccount());
        assertEquals("BTCUSDT", position.symbol());
        assertDecimal("0.25", position.quantity());
        assertDecimal("60000.50", position.averageEntryPrice());
    }

    @Test
    void GIVEN_reorderedColumns_WHEN_loaded_THEN_mapsOpeningPositionByName() throws IOException {
        Path file = temporaryDirectory.resolve("opening-positions.csv");
        Files.writeString(file, """
            avg_entry_price,quantity,symbol,venue_account,venue,trader,timestamp
            42000.75,-0.125,BTCUSDT,ACCOUNT-1,BINANCE,TRADER-A,2026-07-31T23:59:59Z
            """);

        OpeningPosition position = new OpeningPositionCsvLoader().load(file).getFirst();

        assertEquals(Instant.parse("2026-07-31T23:59:59Z"), position.timestamp());
        assertEquals("TRADER-A", position.trader());
        assertEquals("BINANCE", position.venue());
        assertEquals("ACCOUNT-1", position.venueAccount());
        assertEquals("BTCUSDT", position.symbol());
        assertDecimal("-0.125", position.quantity());
        assertDecimal("42000.75", position.averageEntryPrice());
    }

    @Test
    void GIVEN_averageEntryPriceInsteadOfAvgEntryPrice_WHEN_loaded_THEN_throwsCsvDataException()
        throws IOException {
        Path file = temporaryDirectory.resolve("unexpected-header.csv");

        assertCsvError(
            file,
            """
                timestamp,trader,venue,venue_account,symbol,quantity,average_entry_price
                2026-08-01T00:00:00Z,TRADER-A,BINANCE,ACCOUNT-1,BTCUSDT,0.25,60000
                """,
            1,
            "Unexpected header: timestamp,trader,venue,venue_account,symbol,quantity,"
                + "average_entry_price"
        );
    }

    @Test
    void GIVEN_rowWithWrongColumnCount_WHEN_loaded_THEN_throwsCsvDataException()
        throws IOException {
        Path file = temporaryDirectory.resolve("wrong-column-count.csv");

        assertCsvError(
            file,
            """
                timestamp,trader,venue,venue_account,symbol,quantity,avg_entry_price
                2026-08-01T00:00:00Z,TRADER-A,BINANCE,ACCOUNT-1,BTCUSDT,0.25
                """,
            2,
            "Expected 7 columns, found 6"
        );
    }

    @Test
    void GIVEN_emptyField_WHEN_loaded_THEN_throwsCsvDataException() throws IOException {
        Path file = temporaryDirectory.resolve("empty-field.csv");

        assertCsvError(
            file,
            """
                timestamp,trader,venue,venue_account,symbol,quantity,avg_entry_price
                2026-08-01T00:00:00Z,,BINANCE,ACCOUNT-1,BTCUSDT,0.25,60000
                """,
            2,
            "Empty fields are not supported"
        );
    }

    private void assertCsvError(
        Path file,
        String content,
        int lineNumber,
        String detail
    ) throws IOException {
        Files.writeString(file, content);

        CsvDataException exception = assertThrows(
            CsvDataException.class,
            () -> new OpeningPositionCsvLoader().load(file)
        );

        assertEquals(file + ":" + lineNumber + ": " + detail, exception.getMessage());
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros());
    }
}
