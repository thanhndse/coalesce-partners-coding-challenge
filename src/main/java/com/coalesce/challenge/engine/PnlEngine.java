package com.coalesce.challenge.engine;

import com.coalesce.challenge.domain.OpeningPosition;
import com.coalesce.challenge.domain.PnlReport;
import com.coalesce.challenge.domain.PositionKey;
import com.coalesce.challenge.domain.ReportKey;
import com.coalesce.challenge.event.Event;
import com.coalesce.challenge.event.FundingEvent;
import com.coalesce.challenge.event.PriceEvent;
import com.coalesce.challenge.event.TradeEvent;
import com.coalesce.challenge.exception.ConflictingEventException;
import com.coalesce.challenge.exception.LateEventException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** In-memory incremental PnL engine. */
public final class PnlEngine {
    private final Map<PositionKey, PositionState> positions = new HashMap<>();
    private final Map<String, Event> knownEvents = new HashMap<>();
    private final Map<PositionKey, Instant> positionWatermarks = new HashMap<>();
    private final Map<String, Instant> priceWatermarks = new HashMap<>();
    private final Map<String, List<PendingFee>> pendingFeesByConversionSymbol = new HashMap<>();
    private final PriceBook priceBook = new PriceBook();

    public void initialize(List<OpeningPosition> openings) {
        if (!knownEvents.isEmpty()) {
            throw new IllegalStateException("Opening positions must be loaded before events");
        }

        for (OpeningPosition opening : openings) {
            PositionState previous = positions.putIfAbsent(
                opening.key(), PositionState.from(opening)
            );
            if (previous != null) {
                throw new IllegalArgumentException(
                    "Duplicate opening position for " + opening.key()
                );
            }
            positionWatermarks.put(opening.key(), opening.timestamp());
        }
    }

    public ProcessResult process(Event event) {
        Event known = knownEvents.get(event.identity());
        if (known != null) {
            if (known.equals(event)) {
                return ProcessResult.DUPLICATE;
            }
            throw new ConflictingEventException(event.identity());
        }

        switch (event) {
            case TradeEvent trade -> processTrade(trade);
            case FundingEvent funding -> processFunding(funding);
            case PriceEvent price -> processPrice(price);
        }

        knownEvents.put(event.identity(), event);
        return ProcessResult.APPLIED;
    }

    public List<PnlReport> report(Instant valuationTimestamp) {
        Map<ReportKey, ReportAccumulator> totals = new TreeMap<>();

        for (Map.Entry<PositionKey, PositionState> entry : positions.entrySet()) {
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
                Optional<BigDecimal> mark = priceBook.latestPrice(
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
            Optional<BigDecimal> unrealized = value.unrealizedAvailable
                ? Optional.of(value.unrealized)
                : Optional.empty();
            Optional<BigDecimal> fees = value.feesAvailable
                ? Optional.of(value.fees)
                : Optional.empty();
            Optional<BigDecimal> total = unrealized.isPresent() && fees.isPresent()
                ? Optional.of(value.realized
                    .add(unrealized.orElseThrow())
                    .add(value.funding)
                    .subtract(fees.orElseThrow()))
                : Optional.empty();

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

    PositionState position(PositionKey key) {
        return positions.get(key);
    }

    private void processTrade(TradeEvent trade) {
        PositionKey key = trade.positionKey();
        rejectLatePositionEvent(key, trade.timestamp());

        PositionState state = positions.computeIfAbsent(key, ignored -> PositionState.empty());
        PositionCalculator.apply(state, trade);
        applyFee(state, key, trade);
        positionWatermarks.put(key, trade.timestamp());
    }

    private void applyFee(PositionState state, PositionKey key, TradeEvent trade) {
        if (trade.feeAsset().equals("USDT")) {
            state.addFee(trade.fee());
            return;
        }

        String conversionSymbol = trade.feeAsset() + "USDT";
        Optional<BigDecimal> conversionPrice = priceBook.latestPrice(
            conversionSymbol, trade.timestamp()
        );
        if (conversionPrice.isPresent()) {
            state.addFee(trade.fee().multiply(conversionPrice.orElseThrow()));
            return;
        }

        state.addUnresolvedFee();
        pendingFeesByConversionSymbol
            .computeIfAbsent(conversionSymbol, ignored -> new ArrayList<>())
            .add(new PendingFee(key, trade.timestamp(), trade.fee()));
    }

    private void processFunding(FundingEvent funding) {
        if (!funding.asset().equals("USDT")) {
            throw new IllegalArgumentException(
                "Only USDT funding is supported, received " + funding.asset()
            );
        }

        PositionKey key = funding.positionKey();
        rejectLatePositionEvent(key, funding.timestamp());
        PositionState state = positions.computeIfAbsent(key, ignored -> PositionState.empty());
        state.addFundingPnl(funding.amount());
        positionWatermarks.put(key, funding.timestamp());
    }

    private void processPrice(PriceEvent price) {
        Instant watermark = priceWatermarks.get(price.symbol());
        if (watermark != null && price.timestamp().isBefore(watermark)) {
            throw new LateEventException(price.symbol(), price.timestamp(), watermark);
        }

        priceBook.add(price);
        priceWatermarks.put(price.symbol(), price.timestamp());
        resolvePendingFees(price.symbol());
    }

    private void resolvePendingFees(String conversionSymbol) {
        List<PendingFee> pendingFees = pendingFeesByConversionSymbol.get(conversionSymbol);
        if (pendingFees == null) {
            return;
        }

        Iterator<PendingFee> iterator = pendingFees.iterator();
        while (iterator.hasNext()) {
            PendingFee pendingFee = iterator.next();
            Optional<BigDecimal> price = priceBook.latestPrice(
                conversionSymbol, pendingFee.tradeTimestamp()
            );
            if (price.isPresent()) {
                PositionState state = positions.get(pendingFee.positionKey());
                state.resolveFee(pendingFee.amount().multiply(price.orElseThrow()));
                iterator.remove();
            }
        }

        if (pendingFees.isEmpty()) {
            pendingFeesByConversionSymbol.remove(conversionSymbol);
        }
    }

    private void rejectLatePositionEvent(PositionKey key, Instant timestamp) {
        Instant watermark = positionWatermarks.get(key);
        if (watermark != null && timestamp.isBefore(watermark)) {
            throw new LateEventException(key.toString(), timestamp, watermark);
        }
    }

    private record PendingFee(
        PositionKey positionKey,
        Instant tradeTimestamp,
        BigDecimal amount
    ) {
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
