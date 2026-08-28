package com.coalesce.challenge.util;

import java.math.BigDecimal;
import java.util.Objects;

public final class Decimals {
    private Decimals() {
    }

    public static BigDecimal normalize(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }

    public static BigDecimal requirePositive(BigDecimal value, String name) {
        BigDecimal normalized = normalize(value, name);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return normalized;
    }

    public static BigDecimal requireNonNegative(BigDecimal value, String name) {
        BigDecimal normalized = normalize(value, name);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return normalized;
    }
}
