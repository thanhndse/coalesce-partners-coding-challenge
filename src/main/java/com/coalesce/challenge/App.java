package com.coalesce.challenge;

import com.coalesce.challenge.domain.OpeningPosition;
import com.coalesce.challenge.domain.PnlReport;
import com.coalesce.challenge.engine.PnlEngine;
import com.coalesce.challenge.event.Event;
import com.coalesce.challenge.io.FundingCsvLoader;
import com.coalesce.challenge.io.OpeningPositionCsvLoader;
import com.coalesce.challenge.io.PriceCsvLoader;
import com.coalesce.challenge.io.TradeCsvLoader;
import com.coalesce.challenge.report.PnlReportGenerator;
import com.coalesce.challenge.report.TextReportFormatter;
import com.google.inject.Guice;
import com.google.inject.Inject;

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
    private static final String OPENING_POSITIONS_CSV = "opening_positions.csv";
    private static final String TRADES_CSV = "trades.csv";
    private static final String FUNDING_CSV = "funding.csv";
    private static final String PRICES_CSV = "prices.csv";

    private final OpeningPositionCsvLoader openingPositionLoader;
    private final TradeCsvLoader tradeLoader;
    private final FundingCsvLoader fundingLoader;
    private final PriceCsvLoader priceLoader;
    private final PnlEngine engine;
    private final PnlReportGenerator reportGenerator;
    private final TextReportFormatter reportFormatter;

    @Inject
    App(
        OpeningPositionCsvLoader openingPositionLoader,
        TradeCsvLoader tradeLoader,
        FundingCsvLoader fundingLoader,
        PriceCsvLoader priceLoader,
        PnlEngine engine,
        PnlReportGenerator reportGenerator,
        TextReportFormatter reportFormatter
    ) {
        this.openingPositionLoader = openingPositionLoader;
        this.tradeLoader = tradeLoader;
        this.fundingLoader = fundingLoader;
        this.priceLoader = priceLoader;
        this.engine = engine;
        this.reportGenerator = reportGenerator;
        this.reportFormatter = reportFormatter;
    }

    public static void main(String[] args) {
        Guice.createInjector(new ApplicationModule())
            .getInstance(App.class)
            .run(args);
    }

    void run(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: pnl-engine [data-directory]");
        }

        Path dataDirectory = args.length == 1 ? Path.of(args[0]) : Path.of(".");

        List<OpeningPosition> openings = openingPositionLoader.load(
            dataDirectory.resolve(OPENING_POSITIONS_CSV)
        );

        List<Event> events = new ArrayList<>();
        events.addAll(tradeLoader.load(
            dataDirectory.resolve(TRADES_CSV), PERIOD_START, PERIOD_END
        ));
        events.addAll(fundingLoader.load(
            dataDirectory.resolve(FUNDING_CSV), PERIOD_START, PERIOD_END
        ));
        events.addAll(priceLoader.load(
            dataDirectory.resolve(PRICES_CSV), PERIOD_END
        ));
        events.sort(Event.ORDERING);

        engine.initialize(openings);
        events.forEach(engine::process);

        List<PnlReport> reports = reportGenerator.generate(PERIOD_END);
        System.out.print(reportFormatter.format(reports));
    }
}
