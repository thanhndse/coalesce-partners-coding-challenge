package com.coalesce.challenge;

import com.coalesce.challenge.domain.OpeningPosition;
import com.coalesce.challenge.domain.PnlReport;
import com.coalesce.challenge.engine.PnlEngine;
import com.coalesce.challenge.event.Event;
import com.coalesce.challenge.io.CsvDataLoader;
import com.coalesce.challenge.report.TextReportFormatter;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Application entry point for the real-time PnL engine coding challenge.
 */
public final class App {
    static final Instant PERIOD_START = Instant.parse("2026-08-01T00:00:00Z");
    static final Instant PERIOD_END = Instant.parse("2026-08-02T00:00:00Z");

    private App() {
        // Utility class.
    }

    public static void main(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: pnl-engine [data-directory]");
        }

        Path dataDirectory = args.length == 1 ? Path.of(args[0]) : Path.of(".");
        CsvDataLoader loader = new CsvDataLoader();

        List<OpeningPosition> openings = loader.loadOpeningPositions(
            dataDirectory.resolve("opening_positions.csv")
        );

        List<Event> events = new ArrayList<>();
        events.addAll(loader.loadTrades(
            dataDirectory.resolve("trades.csv"), PERIOD_START, PERIOD_END
        ));
        events.addAll(loader.loadFunding(
            dataDirectory.resolve("funding.csv"), PERIOD_START, PERIOD_END
        ));
        events.addAll(loader.loadPrices(
            dataDirectory.resolve("prices.csv"), PERIOD_END
        ));
        events.sort(Event.ORDERING);

        PnlEngine engine = new PnlEngine();
        engine.initialize(openings);
        events.forEach(engine::process);

        List<PnlReport> reports = engine.report(PERIOD_END);
        System.out.print(new TextReportFormatter().format(reports));
    }
}
