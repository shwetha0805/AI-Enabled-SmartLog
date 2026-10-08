package com.smartlog.smartlog.service;

import com.smartlog.smartlog.model.Log;
import org.springframework.stereotype.Service;

@Service
public class FallbackAIService {

    public Log analyze(Log log) {

        String eventType = log.getEventType() == null
                ? ""
                : log.getEventType().toUpperCase();

        String message = log.getMessage() == null
                ? ""
                : log.getMessage().toLowerCase();

        String severity = log.getSeverity() == null
                ? ""
                : log.getSeverity().toUpperCase();

        String label = "NORMAL";

        if (severity.equals("CRITICAL")) {
            label = "ANOMALY";
        }
        else if (severity.equals("HIGH")) {
            label = "ANOMALY";
        }
        else if (eventType.contains("FAILED")) {
            label = "ANOMALY";
        }
        else if (eventType.contains("UNAUTHORIZED")) {
            label = "ANOMALY";
        }
        else if (message.contains("suspicious")) {
            label = "ANOMALY";
        }

        log.setLabel(label);
        log.setAiStatus("FALLBACK");

        return log;
    }
}