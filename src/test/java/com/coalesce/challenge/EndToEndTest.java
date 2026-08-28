package com.coalesce.challenge;

import com.coalesce.challenge.domain.PnlReport;
import com.coalesce.challenge.engine.PnlEngine;
import com.coalesce.challenge.engine.ProcessResult;
import com.coalesce.challenge.event.Event;
import com.coalesce.challenge.io.CsvDataLoader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndToEndTest {
    @Test
    void suppliedDatasetProducesACompleteDeterministicReport() {
        Path dataDirectory = Path.of(".");
        CsvDataLoader loader = new CsvDataLoader();
        List<Event> events = new ArrayList<>();
        events.addAll(loader.loadTrades(
            dataDirectory.resolve("trades.csv"), App.PERIOD_START, App.PERIOD_END
        ));
        events.addAll(loader.loadFunding(
            dataDirectory.resolve("funding.csv"), App.PERIOD_START, App.PERIOD_END
        ));
        events.addAll(loader.loadPrices(
            dataDirectory.resolve("prices.csv"), App.PERIOD_END
        ));
        events.sort(Event.ORDERING);

        PnlEngine engine = new PnlEngine();
        engine.initialize(loader.loadOpeningPositions(
            dataDirectory.resolve("opening_positions.csv")
        ));

        long duplicates = events.stream()
            .map(engine::process)
            .filter(result -> result == ProcessResult.DUPLICATE)
            .count();
        List<PnlReport> reports = engine.report(App.PERIOD_END);

        assertEquals(13, duplicates);
        assertEquals(13, reports.size());
        assertTrue(reports.stream().allMatch(report ->
            report.unrealizedPnl().isPresent()
                && report.fees().isPresent()
                && report.totalPnl().isPresent()
        ));
        assertRounded("34.93", report(reports, "TRADER_A", "BTCUSDT").totalPnl().orElseThrow());
        assertRounded("1461.52", report(reports, "TRADER_C", "BTCUSDT").totalPnl().orElseThrow());
        assertRounded("14.55", report(reports, "TRADER_D", "XRPUSDT").totalPnl().orElseThrow());
    }

    private PnlReport report(List<PnlReport> reports, String trader, String symbol) {
        return reports.stream()
            .filter(report -> report.trader().equals(trader) && report.symbol().equals(symbol))
            .findFirst()
            .orElseThrow();
    }

    private void assertRounded(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual.setScale(2, RoundingMode.HALF_EVEN));
    }
}
