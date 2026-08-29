package com.coalesce.challenge.engine;

import com.coalesce.challenge.event.PriceEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/** Venue-agnostic price histories with a fixed capacity per symbol. */
public final class PriceBook {
    private static final int DEFAULT_MAXIMUM_PRICES_PER_SYMBOL = 50_000;

    private final int maximumPricesPerSymbol;
    private final Map<String, SymbolPrices> pricesBySymbol = new HashMap<>();

    public PriceBook() {
        this(DEFAULT_MAXIMUM_PRICES_PER_SYMBOL);
    }

    public PriceBook(int maximumPricesPerSymbol) {
        if (maximumPricesPerSymbol <= 0) {
            throw new IllegalArgumentException(
                "Maximum prices per symbol must be positive"
            );
        }
        this.maximumPricesPerSymbol = maximumPricesPerSymbol;
    }

    public PriceUpdate add(PriceEvent event) {
        SymbolPrices symbolPrices = pricesBySymbol.computeIfAbsent(
            event.symbol(), ignored -> new SymbolPrices()
        );
        Optional<BigDecimal> previousPrice = Optional.ofNullable(
                symbolPrices.latestAt(event.timestamp())
        ).map(entry -> entry.getValue().price());
        symbolPrices.add(event, maximumPricesPerSymbol);
        return new PriceUpdate(
                event.timestamp(),
                symbolPrices.nextTimestampAfter(event.timestamp()),
                event.symbol(),
                previousPrice,
                symbolPrices.contains(event.timestamp())
        );
    }

    public Optional<BigDecimal> latestPrice(String symbol, Instant asOf) {
        SymbolPrices symbolPrices = pricesBySymbol.get(symbol);
        if (symbolPrices == null) {
            return Optional.empty();
        }

        Map.Entry<Instant, PriceEvent> latestPrice = symbolPrices.latestAt(asOf);
        if (latestPrice == null) {
            return Optional.empty();
        }
        return Optional.of(latestPrice.getValue().price());
    }

    private static final class SymbolPrices {
        private final NavigableMap<Instant, PriceEvent> prices = new TreeMap<>();

        private void add(PriceEvent event, int maximumPrices) {
            prices.put(event.timestamp(), event);
            if (prices.size() > maximumPrices) {
                prices.pollFirstEntry();
            }
        }

        private Map.Entry<Instant, PriceEvent> latestAt(Instant timestamp) {
            return prices.floorEntry(timestamp);
        }

        private Instant nextTimestampAfter(Instant timestamp) {
            return prices.higherKey(timestamp);
        }

        private boolean contains(Instant timestamp) {
            return prices.containsKey(timestamp);
        }
    }

    public record PriceUpdate(
            Instant affectedFromInclusive,
            Instant affectedUntilExclusive,
            String symbol,
            Optional<BigDecimal> previousPrice,
            boolean retained
    ) { }
}
