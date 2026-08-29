package com.coalesce.challenge.report;

import com.coalesce.challenge.ApplicationModule;
import com.coalesce.challenge.domain.OpeningPosition;
import com.coalesce.challenge.domain.PnlReport;
import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.engine.PnlEngine;
import com.coalesce.challenge.event.PriceEvent;
import com.coalesce.challenge.event.TradeEvent;
import com.coalesce.challenge.state.EngineState;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PnlReportGeneratorTest {
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void GIVEN_nonUsdtFeeAndPrice_WHEN_reportGenerated_THEN_convertsFeeAndTotalPnl() {
        ReportFixture fixture = initializedFixture();
        fixture.engine().process(price("BNBUSDT", START.plusSeconds(5), "800"));
        fixture.engine().process(trade("T1", "ACCOUNT", START.plusSeconds(10),
            "1", "100", "0.01", "BNB"));
        fixture.engine().process(price("BTCUSDT", START.plusSeconds(20), "110"));

        PnlReport report = fixture.generator().generate(START.plusSeconds(20)).getFirst();

        assertDecimal("8", report.fees());
        assertDecimal("10", report.unrealizedPnl());
        assertDecimal("2", report.totalPnl());
    }

    @Test
    void GIVEN_unresolvedFee_WHEN_eligiblePriceArrivesLater_THEN_resolvesFee() {
        ReportFixture fixture = initializedFixture();
        fixture.engine().process(trade("T1", "ACCOUNT", START.plusSeconds(10),
            "1", "100", "0.01", "BNB"));
        fixture.engine().process(price("BTCUSDT", START.plusSeconds(20), "110"));

        fixture.engine().process(price("BNBUSDT", START.plusSeconds(5), "800"));
        PnlReport report = fixture.generator().generate(START.plusSeconds(20)).getFirst();

        assertDecimal("8", report.fees());
        assertDecimal("2", report.totalPnl());
    }

    @Test
    void GIVEN_missingMark_WHEN_reportGenerated_THEN_marksDependentAmountsUnavailable() {
        ReportFixture fixture = initializedFixture(opening("ACCOUNT", "1", "100"));

        PnlReport report = fixture.generator().generate(START.plusSeconds(100)).getFirst();

        assertNull(report.unrealizedPnl());
        assertNull(report.totalPnl());
        assertDecimal("0", report.realizedPnl());
        assertDecimal("0", report.fees());
    }

    @Test
    void GIVEN_multipleAccounts_WHEN_reportGenerated_THEN_valuesBeforeAggregating() {
        ReportFixture fixture = initializedFixture(
            opening("LONG_ACCOUNT", "1", "100"),
            opening("SHORT_ACCOUNT", "-1", "90")
        );
        fixture.engine().process(price("BTCUSDT", START.plusSeconds(10), "110"));

        PnlReport report = fixture.generator().generate(START.plusSeconds(10)).getFirst();

        assertDecimal("0", report.finalQuantity());
        assertDecimal("-10", report.unrealizedPnl());
    }

    private ReportFixture initializedFixture(OpeningPosition... openings) {
        Injector injector = Guice.createInjector(new ApplicationModule());
        PnlEngine engine = injector.getInstance(PnlEngine.class);
        engine.initialize(List.of(openings));
        return new ReportFixture(
            engine,
            injector.getInstance(PnlReportGenerator.class),
            injector.getInstance(EngineState.class)
        );
    }

    private OpeningPosition opening(String account, String quantity, String average) {
        return new OpeningPosition(
            START, "TRADER", "VENUE", account, "BTCUSDT",
            new BigDecimal(quantity), new BigDecimal(average)
        );
    }

    private TradeEvent trade(
        String id,
        String account,
        Instant timestamp,
        String quantity,
        String price,
        String fee,
        String feeAsset
    ) {
        return new TradeEvent(
            timestamp, "VENUE", id, "TRADER", account, "BTCUSDT", Side.BUY,
            new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(fee), feeAsset
        );
    }

    private PriceEvent price(String symbol, Instant timestamp, String price) {
        return new PriceEvent(timestamp, symbol, new BigDecimal(price));
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros());
    }

    private record ReportFixture(
        PnlEngine engine,
        PnlReportGenerator generator,
        EngineState engineState
    ) {}
}
