package com.coalesce.challenge.engine;

import com.coalesce.challenge.event.TradeEvent;

import java.math.BigDecimal;
import java.util.Optional;

/** Calculates a trade fee in the reporting asset from retained market prices. */
public final class ReportingFeeCalculator {
    private static final String REPORTING_ASSET = "USDT";

    private ReportingFeeCalculator() {
    }

    public static Optional<BigDecimal> calculate(
            TradeEvent trade,
            PriceBook priceBook
    ) {
        if (REPORTING_ASSET.equals(trade.feeAsset())) {
            return Optional.of(trade.fee());
        }
        return priceBook.latestPrice(conversionSymbol(trade), trade.timestamp())
                .map(trade.fee()::multiply);
    }

    static String conversionSymbol(TradeEvent trade) {
        if (REPORTING_ASSET.equals(trade.feeAsset())) {
            return null;
        }
        return trade.feeAsset() + REPORTING_ASSET;
    }
}
