package com.coalesce.challenge.engine;

import com.coalesce.challenge.domain.OpeningPosition;

import java.math.BigDecimal;

/** Mutable account-level state owned by {@link PnlEngine}. */
public final class PositionState {
    private BigDecimal quantity;
    private BigDecimal averageEntryPrice;
    private BigDecimal realizedPnl;
    private BigDecimal fundingPnl;
    private BigDecimal fees;
    private int unresolvedFeeCount;

    private PositionState(BigDecimal quantity, BigDecimal averageEntryPrice) {
        this.quantity = quantity;
        this.averageEntryPrice = averageEntryPrice;
        this.realizedPnl = BigDecimal.ZERO;
        this.fundingPnl = BigDecimal.ZERO;
        this.fees = BigDecimal.ZERO;
    }

    public static PositionState from(OpeningPosition opening) {
        return new PositionState(opening.quantity(), opening.averageEntryPrice());
    }

    public static PositionState empty() {
        return new PositionState(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    PositionState copy() {
        PositionState copy = new PositionState(quantity, averageEntryPrice);
        copy.realizedPnl = realizedPnl;
        copy.fundingPnl = fundingPnl;
        copy.fees = fees;
        copy.unresolvedFeeCount = unresolvedFeeCount;
        return copy;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public BigDecimal averageEntryPrice() {
        return averageEntryPrice;
    }

    public BigDecimal realizedPnl() {
        return realizedPnl;
    }

    public BigDecimal fundingPnl() {
        return fundingPnl;
    }

    public BigDecimal fees() {
        return fees;
    }

    public boolean feesAvailable() {
        return unresolvedFeeCount == 0;
    }

    void replacePosition(BigDecimal newQuantity, BigDecimal newAverageEntryPrice) {
        quantity = newQuantity;
        averageEntryPrice = newAverageEntryPrice;
    }

    void addRealizedPnl(BigDecimal amount) {
        realizedPnl = realizedPnl.add(amount);
    }

    public void addFundingPnl(BigDecimal amount) {
        fundingPnl = fundingPnl.add(amount);
    }

    public void addFee(BigDecimal amount) {
        fees = fees.add(amount);
    }

    public void addUnresolvedFee() {
        unresolvedFeeCount++;
    }

    public void resolveFee(BigDecimal amount) {
        if (unresolvedFeeCount == 0) {
            throw new IllegalStateException("No unresolved fee to resolve");
        }
        addFee(amount);
        unresolvedFeeCount--;
    }

}
