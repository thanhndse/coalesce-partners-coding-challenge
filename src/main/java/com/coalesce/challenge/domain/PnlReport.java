package com.coalesce.challenge.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Aggregated PnL for one trader and symbol. */
public record PnlReport(
    String trader,
    String symbol,
    BigDecimal finalQuantity,
    BigDecimal realizedPnl,
    Optional<BigDecimal> unrealizedPnl,
    BigDecimal fundingPnl,
    Optional<BigDecimal> fees,
    Optional<BigDecimal> totalPnl
) {
    public PnlReport {
        Objects.requireNonNull(trader, "trader");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(finalQuantity, "finalQuantity");
        Objects.requireNonNull(realizedPnl, "realizedPnl");
        Objects.requireNonNull(unrealizedPnl, "unrealizedPnl");
        Objects.requireNonNull(fundingPnl, "fundingPnl");
        Objects.requireNonNull(fees, "fees");
        Objects.requireNonNull(totalPnl, "totalPnl");
    }
}
