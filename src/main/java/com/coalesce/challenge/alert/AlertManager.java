package com.coalesce.challenge.alert;

public interface AlertManager {
    void raise(AlertSeverity severity, String message);
}
