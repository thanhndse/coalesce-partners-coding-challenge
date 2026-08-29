package com.coalesce.challenge.alert;

public final class LoggingAlertManager implements AlertManager {
    private static final System.Logger LOGGER = System.getLogger(
        LoggingAlertManager.class.getName()
    );

    @Override
    public void raise(AlertSeverity severity, String message) {
        LOGGER.log(logLevel(severity), "[{0}] {1}", severity, message);
    }

    private System.Logger.Level logLevel(AlertSeverity severity) {
        return switch (severity) {
            case REQUIRES_IMMEDIATE_ACTION -> System.Logger.Level.ERROR;
            case NEED_TO_BE_NOTIFIED, BUSINESS_ALERT -> System.Logger.Level.WARNING;
            case SELF_RECOVERABLE, INFORMATIONAL -> System.Logger.Level.INFO;
        };
    }
}
