package com.coalesce.challenge.report;

import com.coalesce.challenge.domain.PnlReport;
import com.coalesce.challenge.domain.PositionKey;
import com.coalesce.challenge.domain.ReportKey;
import com.coalesce.challenge.engine.PositionState;
import com.coalesce.challenge.state.EngineState;
import com.google.inject.Inject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Builds trader-and-symbol PnL reports from the current application state. */
public final class PnlReportGenerator {
    private final EngineState engineState;

    @Inject
    public PnlReportGenerator(EngineState engineState) {
        this.engineState = engineState;
    }

    public List<PnlReport> generate(Instant valuationTimestamp) {
        Map<ReportKey, ReportAccumulator> totals = new TreeMap<>();

        for (Map.Entry<PositionKey, PositionState> entry : engineState.positions().entrySet()) {
            PositionKey positionKey = entry.getKey();
            PositionState state = entry.getValue();
            ReportKey reportKey = new ReportKey(positionKey.trader(), positionKey.symbol());
            ReportAccumulator accumulator = totals.computeIfAbsent(
                reportKey, ignored -> new ReportAccumulator()
            );

            accumulator.quantity = accumulator.quantity.add(state.quantity());
            accumulator.realized = accumulator.realized.add(state.realizedPnl());
            accumulator.funding = accumulator.funding.add(state.fundingPnl());

            if (state.feesAvailable()) {
                accumulator.fees = accumulator.fees.add(state.fees());
            } else {
                accumulator.feesAvailable = false;
            }

            if (state.quantity().signum() != 0) {
                Optional<BigDecimal> mark = engineState.priceBook().latestPrice(
                    positionKey.symbol(), valuationTimestamp
                );
                if (mark.isPresent()) {
                    BigDecimal accountUnrealized = state.quantity()
                        .multiply(mark.get().subtract(state.averageEntryPrice()));
                    accumulator.unrealized = accumulator.unrealized.add(accountUnrealized);
                } else {
                    accumulator.unrealizedAvailable = false;
                }
            }
        }

        List<PnlReport> reports = new ArrayList<>();
        for (Map.Entry<ReportKey, ReportAccumulator> entry : totals.entrySet()) {
            ReportKey key = entry.getKey();
            ReportAccumulator value = entry.getValue();
            BigDecimal unrealized = value.unrealizedAvailable ? value.unrealized : null;
            BigDecimal fees = value.feesAvailable ? value.fees : null;
            BigDecimal total = unrealized != null && fees != null
                ? value.realized
                    .add(unrealized)
                    .add(value.funding)
                    .subtract(fees)
                : null;

            reports.add(new PnlReport(
                key.trader(),
                key.symbol(),
                value.quantity,
                value.realized,
                unrealized,
                value.funding,
                fees,
                total
            ));
        }
        return List.copyOf(reports);
    }

    private static final class ReportAccumulator {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal realized = BigDecimal.ZERO;
        private BigDecimal unrealized = BigDecimal.ZERO;
        private BigDecimal funding = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;
        private boolean unrealizedAvailable = true;
        private boolean feesAvailable = true;
    }
}
