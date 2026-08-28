package com.coalesce.challenge.engine;

import com.coalesce.challenge.event.PriceEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/** Ordered venue-agnostic mark and conversion prices. */
public final class PriceBook {
    private final Map<String, NavigableMap<Instant, BigDecimal>> prices = new HashMap<>();

    public void add(PriceEvent event) {
        prices.computeIfAbsent(event.symbol(), ignored -> new TreeMap<>())
            .put(event.timestamp(), event.price());
    }

    public Optional<BigDecimal> latestPrice(String symbol, Instant asOf) {
        NavigableMap<Instant, BigDecimal> symbolPrices = prices.get(symbol);
        if (symbolPrices == null) {
            return Optional.empty();
        }

        Map.Entry<Instant, BigDecimal> match = symbolPrices.floorEntry(asOf);
        return match == null ? Optional.empty() : Optional.of(match.getValue());
    }
}
