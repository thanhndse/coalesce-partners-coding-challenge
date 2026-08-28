package com.coalesce.challenge.domain;

import java.util.Objects;

/** Final reporting grain. */
public record ReportKey(String trader, String symbol) implements Comparable<ReportKey> {
    public ReportKey {
        Objects.requireNonNull(trader, "trader");
        Objects.requireNonNull(symbol, "symbol");
    }

    @Override
    public int compareTo(ReportKey other) {
        int byTrader = trader.compareTo(other.trader);
        return byTrader != 0 ? byTrader : symbol.compareTo(other.symbol);
    }
}
