package com.coalesce.challenge.report;

import com.coalesce.challenge.domain.PnlReport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TextReportFormatter {
    public String format(List<PnlReport> reports) {
        StringBuilder output = new StringBuilder();
        String currentTrader = null;

        for (PnlReport report : reports) {
            if (!report.trader().equals(currentTrader)) {
                if (currentTrader != null) {
                    output.append(System.lineSeparator());
                }
                currentTrader = report.trader();
                output.append(currentTrader).append(System.lineSeparator());
            }

            output.append("  ").append(report.symbol()).append(System.lineSeparator());
            appendLine(output, "Final Quantity", formatQuantity(report.finalQuantity()));
            appendLine(output, "Realized PnL", formatSigned(report.realizedPnl()));
            appendLine(output, "Unrealized PnL", formatOptionalSigned(report.unrealizedPnl()));
            appendLine(output, "Funding PnL", formatSigned(report.fundingPnl()));
            appendLine(output, "Fees", formatOptionalUnsigned(report.fees()));
            output.append("    --------------------------------").append(System.lineSeparator());
            appendLine(output, "Total PnL", formatOptionalSigned(report.totalPnl()));
        }

        return output.toString();
    }

    private void appendLine(StringBuilder output, String label, String value) {
        output.append(String.format(Locale.ROOT, "    %-20s %15s%n", label, value));
    }

    private String formatQuantity(BigDecimal quantity) {
        return quantity.signum() == 0 ? "0" : quantity.stripTrailingZeros().toPlainString();
    }

    private String formatOptionalSigned(Optional<BigDecimal> value) {
        return value.map(this::formatSigned).orElse("UNAVAILABLE");
    }

    private String formatOptionalUnsigned(Optional<BigDecimal> value) {
        return value.map(this::formatUnsigned).orElse("UNAVAILABLE");
    }

    private String formatSigned(BigDecimal value) {
        return decimalFormat("+#,##0.00;-#,##0.00").format(value);
    }

    private String formatUnsigned(BigDecimal value) {
        return decimalFormat("#,##0.00").format(value);
    }

    private DecimalFormat decimalFormat(String pattern) {
        DecimalFormat format = new DecimalFormat(
            pattern, DecimalFormatSymbols.getInstance(Locale.US)
        );
        format.setRoundingMode(RoundingMode.HALF_EVEN);
        return format;
    }
}
