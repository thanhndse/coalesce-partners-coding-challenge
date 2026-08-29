package com.coalesce.challenge.domain;

import java.time.Instant;

public record PriceKey(String symbol, Instant timestamp) {
}
