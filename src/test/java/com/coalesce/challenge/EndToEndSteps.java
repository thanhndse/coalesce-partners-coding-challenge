package com.coalesce.challenge;

import com.coalesce.challenge.alert.AlertManager;
import com.coalesce.challenge.alert.AlertSeverity;
import com.coalesce.challenge.domain.OpeningPosition;
import com.coalesce.challenge.domain.PnlReport;
import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.engine.PnlEngine;
import com.coalesce.challenge.engine.PositionState;
import com.coalesce.challenge.event.Event;
import com.coalesce.challenge.event.FundingEvent;
import com.coalesce.challenge.event.PriceEvent;
import com.coalesce.challenge.event.TradeEvent;
import com.coalesce.challenge.io.FundingCsvLoader;
import com.coalesce.challenge.io.OpeningPositionCsvLoader;
import com.coalesce.challenge.io.PriceCsvLoader;
import com.coalesce.challenge.io.TradeCsvLoader;
import com.coalesce.challenge.report.PnlReportGenerator;
import com.coalesce.challenge.state.EngineState;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EndToEndSteps {
    private Injector injector;
    private PnlEngine engine;
    private OpeningPosition openingPosition;
    private RecordingAlertManager alerts;
    private List<PnlReport> reports;

    @Given("the standard CSV dataset")
    public void theStandardCsvDataset() {
        injector = Guice.createInjector(new ApplicationModule());
        engine = injector.getInstance(PnlEngine.class);

        Path dataDirectory = Path.of(System.getProperty("user.dir"));
        List<Event> events = new ArrayList<>();
        events.addAll(injector.getInstance(TradeCsvLoader.class).load(
                dataDirectory.resolve("trades.csv"), App.PERIOD_START, App.PERIOD_END
        ));
        events.addAll(injector.getInstance(FundingCsvLoader.class).load(
                dataDirectory.resolve("funding.csv"), App.PERIOD_START, App.PERIOD_END
        ));
        events.addAll(injector.getInstance(PriceCsvLoader.class).load(
                dataDirectory.resolve("prices.csv"), App.PERIOD_END
        ));
        events.sort(Event.ORDERING);

        engine.initialize(injector.getInstance(OpeningPositionCsvLoader.class).load(
                dataDirectory.resolve("opening_positions.csv")
        ));
        events.forEach(engine::process);
    }

    @Given("the engine has this opening position:")
    public void anEngineWithOpeningPosition(DataTable table) {
        alerts = new RecordingAlertManager();
        initializeEngine(table, injectorWith(alerts));
    }

    private void initializeEngine(DataTable table, Injector configuredInjector) {
        Map<String, String> row = onlyRow(table);
        injector = configuredInjector;
        engine = injector.getInstance(PnlEngine.class);
        openingPosition = new OpeningPosition(
                Instant.parse(row.get("timestamp")), row.get("trader"), row.get("venue"),
                row.get("account"), row.get("symbol"), decimal(row.get("quantity")),
                decimal(row.get("averageEntryPrice"))
        );
        engine.initialize(List.of(openingPosition));
    }

    @When("the report is generated at the period end")
    public void generateReportAtPeriodEnd() {
        generateReport(App.PERIOD_END.toString());
    }

    @Given("these events have been processed in order:")
    public void theseEventsHaveBeenProcessedInOrder(DataTable table) {
        table.asMaps().stream().map(this::toEvent).forEach(engine::process);
    }

    @When("the report is generated at {word}")
    public void generateReport(String timestamp) {
        reports = injector.getInstance(PnlReportGenerator.class).generate(Instant.parse(timestamp));
    }

    @Then("the report contains:")
    public void theReportContains(DataTable table) {
        List<Map<String, String>> expected = table.asMaps();
        assertEquals(expected.size(), reports.size());
        for (int index = 0; index < expected.size(); index++) {
            assertReport(expected.get(index), reports.get(index));
        }
    }

    @Then("the opening position state is:")
    public void theOpeningPositionStateIs(DataTable table) {
        PositionState actual = injector.getInstance(EngineState.class)
                .positions().get(openingPosition.key());
        Map<String, String> expected = onlyRow(table);
        assertDecimal(expected.get("quantity"), actual.quantity());
        assertDecimal(expected.get("averageEntryPrice"), actual.averageEntryPrice());
        assertDecimal(expected.get("realizedPnl"), actual.realizedPnl());
        assertDecimal(expected.get("fundingPnl"), actual.fundingPnl());
        assertDecimal(expected.get("fees"), actual.fees());
        assertEquals(Boolean.parseBoolean(expected.get("feesAvailable")), actual.feesAvailable());
    }

    @Then("these alerts were raised in order:")
    public void theseAlertsWereRaisedInOrder(DataTable table) {
        List<RaisedAlert> expected = table.asMaps().stream()
                .map(row -> new RaisedAlert(
                        AlertSeverity.valueOf(row.get("severity")), row.get("message")
                ))
                .toList();
        assertEquals(expected, alerts.raisedAlerts());
    }

    private Event toEvent(Map<String, String> row) {
        return switch (row.get("type")) {
            case "PRICE" -> toPrice(row);
            case "TRADE" -> toTrade(row);
            case "FUNDING" -> new FundingEvent(
                    Instant.parse(row.get("timestamp")), row.get("id"), row.get("trader"),
                    row.get("venue"), row.get("account"), row.get("symbol"),
                    valueOrDefault(row, "asset", "USDT"), decimal(row.get("amount"))
            );
            default -> throw new IllegalArgumentException("Unknown event type: " + row.get("type"));
        };
    }

    private PriceEvent toPrice(Map<String, String> row) {
        return new PriceEvent(
                Instant.parse(row.get("timestamp")),
                row.get("symbol"),
                decimal(row.get("price"))
        );
    }

    private TradeEvent toTrade(Map<String, String> row) {
        return new TradeEvent(
                Instant.parse(row.get("timestamp")), row.get("venue"), row.get("id"),
                row.get("trader"), row.get("account"), row.get("symbol"),
                Side.valueOf(row.get("side")),
                decimal(row.get("quantity")), decimal(row.get("price")), decimal(row.get("fee")),
                row.get("asset")
        );
    }

    private void assertReport(Map<String, String> expected, PnlReport actual) {
        assertEquals(expected.get("trader"), actual.trader());
        assertEquals(expected.get("symbol"), actual.symbol());
        assertDecimal(expected.get("finalQuantity"), actual.finalQuantity());
        assertRounded(expected.get("realizedPnl"), actual.realizedPnl());
        assertRoundedOrUnavailable(expected.get("unrealizedPnl"), actual.unrealizedPnl());
        assertRounded(expected.get("fundingPnl"), actual.fundingPnl());
        assertRoundedOrUnavailable(expected.get("fees"), actual.fees());
        assertRoundedOrUnavailable(expected.get("totalPnl"), actual.totalPnl());
    }

    private Injector injectorWith(AlertManager alertManager) {
        return Guice.createInjector(Modules.override(new ApplicationModule()).with(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(AlertManager.class).toInstance(alertManager);
                    }
                }
        ));
    }

    private Map<String, String> onlyRow(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        assertEquals(1, rows.size());
        return rows.getFirst();
    }

    private String valueOrDefault(Map<String, String> row, String key, String fallback) {
        String value = row.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(decimal(expected).stripTrailingZeros(), actual.stripTrailingZeros());
    }

    private void assertRounded(String expected, BigDecimal actual) {
        assertEquals(decimal(expected), actual.setScale(2, RoundingMode.HALF_EVEN));
    }

    private void assertRoundedOrUnavailable(String expected, BigDecimal actual) {
        if ("UNAVAILABLE".equals(expected)) {
            assertNull(actual);
            return;
        }
        assertRounded(expected, actual);
    }

    private record RaisedAlert(AlertSeverity severity, String message) {
    }

    private static final class RecordingAlertManager implements AlertManager {
        private final List<RaisedAlert> raisedAlerts = new ArrayList<>();

        @Override
        public void raise(AlertSeverity severity, String message) {
            raisedAlerts.add(new RaisedAlert(severity, message));
        }

        private List<RaisedAlert> raisedAlerts() {
            return List.copyOf(raisedAlerts);
        }
    }
}
