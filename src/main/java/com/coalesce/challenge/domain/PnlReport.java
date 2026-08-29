package com.coalesce.challenge.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Aggregated PnL for one trader and symbol; nullable amounts are unavailable. */
public record PnlReport(
    String trader,
    String symbol,
    BigDecimal finalQuantity,
    BigDecimal realizedPnl,
    BigDecimal unrealizedPnl,
    BigDecimal fundingPnl,
    BigDecimal fees,
    BigDecimal totalPnl
) {
    public PnlReport {
        Objects.requireNonNull(trader, "trader");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(finalQuantity, "finalQuantity");
        Objects.requireNonNull(realizedPnl, "realizedPnl");
        Objects.requireNonNull(fundingPnl, "fundingPnl");
    }
}
