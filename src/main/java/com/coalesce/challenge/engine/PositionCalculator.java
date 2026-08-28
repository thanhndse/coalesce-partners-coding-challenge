package com.coalesce.challenge.engine;

import com.coalesce.challenge.domain.Side;
import com.coalesce.challenge.event.TradeEvent;

import java.math.BigDecimal;
import java.math.MathContext;

/** Weighted-average cost position transitions for long and short futures positions. */
public final class PositionCalculator {
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    private PositionCalculator() {
    }

    public static void apply(PositionState state, TradeEvent trade) {
        BigDecimal oldQuantity = state.quantity();
        BigDecimal signedTradeQuantity = trade.side() == Side.BUY
            ? trade.quantity()
            : trade.quantity().negate();
        BigDecimal newQuantity = oldQuantity.add(signedTradeQuantity);

        if (oldQuantity.signum() == 0) {
            state.replacePosition(newQuantity, trade.price());
            return;
        }

        if (oldQuantity.signum() == signedTradeQuantity.signum()) {
            BigDecimal oldCost = oldQuantity.abs().multiply(state.averageEntryPrice());
            BigDecimal addedCost = signedTradeQuantity.abs().multiply(trade.price());
            BigDecimal newAverage = oldCost.add(addedCost)
                .divide(newQuantity.abs(), CALCULATION_CONTEXT);
            state.replacePosition(newQuantity, newAverage);
            return;
        }

        BigDecimal closingQuantity = oldQuantity.abs().min(signedTradeQuantity.abs());
        BigDecimal realizedPerUnit = oldQuantity.signum() > 0
            ? trade.price().subtract(state.averageEntryPrice())
            : state.averageEntryPrice().subtract(trade.price());
        state.addRealizedPnl(closingQuantity.multiply(realizedPerUnit));

        if (newQuantity.signum() == 0) {
            state.replacePosition(BigDecimal.ZERO, BigDecimal.ZERO);
        } else if (newQuantity.signum() == oldQuantity.signum()) {
            state.replacePosition(newQuantity, state.averageEntryPrice());
        } else {
            // The trade closed the old side and opened the remainder on the opposite side.
            state.replacePosition(newQuantity, trade.price());
        }
    }
}
