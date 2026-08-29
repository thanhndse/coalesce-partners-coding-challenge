package com.coalesce.challenge.io;

import com.coalesce.challenge.exception.CsvDataException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class CsvFileReader {
    private CsvFileReader() {
    }

    static List<Row> readRows(Path path, List<String> expectedColumns) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

            try (CSVParser parser = format.parse(reader)) {
                validateHeader(path, parser, expectedColumns);
                List<Row> rows = new ArrayList<>();
                for (CSVRecord record : parser) {
                    int lineNumber = Math.toIntExact(parser.getCurrentLineNumber());
                    validateRow(path, lineNumber, record, expectedColumns);
                    rows.add(new Row(lineNumber, record));
                }
                return List.copyOf(rows);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read " + path, exception);
        }
    }

    static CsvDataException invalidRow(Path path, Row row, RuntimeException exception) {
        if (exception instanceof CsvDataException csvDataException) {
            return csvDataException;
        }
        return new CsvDataException(path, row.lineNumber(), exception.getMessage(), exception);
    }

    private static void validateHeader(
        Path path,
        CSVParser parser,
        List<String> expectedColumns
    ) {
        List<String> actualColumns = parser.getHeaderNames();
        if (actualColumns.size() != expectedColumns.size()
            || !new HashSet<>(actualColumns).equals(new HashSet<>(expectedColumns))) {
            throw new CsvDataException(
                path,
                1,
                "Unexpected header: " + String.join(",", actualColumns)
            );
        }
    }

    private static void validateRow(
        Path path,
        int lineNumber,
        CSVRecord record,
        List<String> expectedColumns
    ) {
        if (record.size() != expectedColumns.size()) {
            throw new CsvDataException(
                path,
                lineNumber,
                "Expected " + expectedColumns.size() + " columns, found " + record.size()
            );
        }

        for (String column : expectedColumns) {
            if (record.get(column).isEmpty()) {
                throw new CsvDataException(path, lineNumber, "Empty fields are not supported");
            }
        }
    }

    record Row(int lineNumber, CSVRecord record) {}
}
