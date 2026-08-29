package com.coalesce.challenge.engine;

import com.coalesce.challenge.ApplicationModule;
import com.coalesce.challenge.domain.OpeningPosition;
import com.coalesce.challenge.domain.PnlReport;
import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.event.FundingEvent;
import com.coalesce.challenge.event.TradeEvent;
import com.coalesce.challenge.report.PnlReportGenerator;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PnlEngineTest {
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void GIVEN_repeatedEvents_WHEN_processed_THEN_appliesEachEventOnce() {
        Fixture fixture = initializedFixture(opening("ACCOUNT", "1", "100"));
        TradeEvent trade = trade("T1", "ACCOUNT", START.plusSeconds(10), Side.BUY,
            "1", "110", "2", "USDT");
        FundingEvent funding = funding("F1", "ACCOUNT", START.plusSeconds(10), "-5");

        fixture.engine().process(trade);
        fixture.engine().process(trade);
        fixture.engine().process(funding);
        fixture.engine().process(funding);

        PnlReport report = fixture.reportGenerator().generate(START.plusSeconds(20)).getFirst();
        assertDecimal("2", report.finalQuantity());
        assertDecimal("2", report.fees());
        assertDecimal("-5", report.fundingPnl());
    }

    private Fixture initializedFixture(OpeningPosition... openings) {
        Injector injector = Guice.createInjector(new ApplicationModule());
        PnlEngine engine = injector.getInstance(PnlEngine.class);
        engine.initialize(List.of(openings));
        return new Fixture(engine, injector.getInstance(PnlReportGenerator.class));
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
        Side side,
        String quantity,
        String price,
        String fee,
        String feeAsset
    ) {
        return new TradeEvent(
            timestamp, "VENUE", id, "TRADER", account, "BTCUSDT", side,
            new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(fee), feeAsset
        );
    }

    private FundingEvent funding(
        String id,
        String account,
        Instant timestamp,
        String amount
    ) {
        return new FundingEvent(
            timestamp, id, "TRADER", "VENUE", account, "BTCUSDT", "USDT",
            new BigDecimal(amount)
        );
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros());
    }

    private record Fixture(PnlEngine engine, PnlReportGenerator reportGenerator) {
    }
}
