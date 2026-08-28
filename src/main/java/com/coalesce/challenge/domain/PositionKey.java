package com.coalesce.challenge.domain;

import java.util.Objects;

/** Account-level position partition. */
public record PositionKey(
    String trader,
    String venue,
    String venueAccount,
    String symbol
) {
    public PositionKey {
        Objects.requireNonNull(trader, "trader");
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(venueAccount, "venueAccount");
        Objects.requireNonNull(symbol, "symbol");
    }
}
