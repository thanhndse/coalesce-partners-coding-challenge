package com.coalesce.challenge.engine;

import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.event.FundingEvent;
import com.coalesce.challenge.event.TradeEvent;

import java.math.BigDecimal;
import java.math.MathContext;

/** Applies trade and funding accounting effects to a position. */
public final class PositionCalculator {
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    private PositionCalculator() {
    }

    public static void applyTrade(
            PositionState state,
            TradeEvent trade,
            PriceBook priceBook
    ) {
        BigDecimal oldQuantity = state.quantity();
        BigDecimal tradeQuantity = trade.side() == Side.BUY
            ? trade.quantity()
            : trade.quantity().negate();

        if (positionQtyZero(oldQuantity)) {
            openPosition(state, tradeQuantity, trade.price());
        } else if (isIncreasingPosition(oldQuantity, tradeQuantity)) {
            increasePosition(state, tradeQuantity, trade.price());
        } else {
            realizeClosingPnl(state, oldQuantity, tradeQuantity, trade.price());

            if (isClosingPosition(oldQuantity, tradeQuantity)) {
                closePosition(state);
            } else if (isReducingPosition(oldQuantity, tradeQuantity)) {
                reducePosition(state, tradeQuantity);
            } else {
                reversePosition(state, tradeQuantity, trade.price());
            }
        }

        ReportingFeeCalculator.calculate(trade, priceBook)
                .ifPresentOrElse(state::addFee, state::addUnresolvedFee);
    }

    public static void applyFunding(
            PositionState state,
            FundingEvent funding
    ) {
        state.addFundingPnl(funding.amount());
    }

    private static boolean positionQtyZero(BigDecimal oldQuantity) {
        return oldQuantity.signum() == 0;
    }

    private static boolean isIncreasingPosition(
        BigDecimal oldQuantity,
        BigDecimal tradeQuantity
    ) {
        return oldQuantity.signum() == tradeQuantity.signum();
    }

    private static boolean isClosingPosition(
        BigDecimal oldQuantity,
        BigDecimal tradeQuantity
    ) {
        return oldQuantity.add(tradeQuantity).signum() == 0;
    }

    private static boolean isReducingPosition(
        BigDecimal oldQuantity,
        BigDecimal tradeQuantity
    ) {
        return oldQuantity.add(tradeQuantity).signum() == oldQuantity.signum();
    }

    private static void openPosition(
        PositionState state,
        BigDecimal tradeQuantity,
        BigDecimal tradePrice
    ) {
        state.replacePosition(state.quantity().add(tradeQuantity), tradePrice);
    }

    private static void increasePosition(
        PositionState state,
        BigDecimal tradeQuantity,
        BigDecimal tradePrice
    ) {
        BigDecimal newQuantity = state.quantity().add(tradeQuantity);
        BigDecimal oldCost = state.quantity().abs().multiply(state.averageEntryPrice());
        BigDecimal addedCost = tradeQuantity.abs().multiply(tradePrice);
        BigDecimal newAverage = oldCost.add(addedCost)
            .divide(newQuantity.abs(), CALCULATION_CONTEXT);
        state.replacePosition(newQuantity, newAverage);
    }

    private static void realizeClosingPnl(
        PositionState state,
        BigDecimal oldQuantity,
        BigDecimal tradeQuantity,
        BigDecimal tradePrice
    ) {
        BigDecimal closingQuantity = oldQuantity.abs().min(tradeQuantity.abs());
        BigDecimal realizedPerUnit = oldQuantity.signum() > 0
            ? tradePrice.subtract(state.averageEntryPrice())
            : state.averageEntryPrice().subtract(tradePrice);
        state.addRealizedPnl(closingQuantity.multiply(realizedPerUnit));
    }

    private static void closePosition(PositionState state) {
        state.replacePosition(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static void reducePosition(
        PositionState state,
        BigDecimal tradeQuantity
    ) {
        state.replacePosition(
            state.quantity().add(tradeQuantity), state.averageEntryPrice()
        );
    }

    private static void reversePosition(
        PositionState state,
        BigDecimal tradeQuantity,
        BigDecimal tradePrice
    ) {
        state.replacePosition(state.quantity().add(tradeQuantity), tradePrice);
    }
}
