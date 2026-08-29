package com.coalesce.challenge.domain;

import java.util.Objects;

public record TradeKey(String venue, String tradeId) {
    public TradeKey {
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(tradeId, "tradeId");
    }
}
