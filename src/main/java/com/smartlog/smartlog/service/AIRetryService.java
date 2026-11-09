
package com.smartlog.smartlog.service;

import com.smartlog.smartlog.model.Log;
import com.smartlog.smartlog.repository.LogRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AIRetryService {

    private final LogRepository logRepository;
    private final AIService aiService;

    public AIRetryService(
            LogRepository logRepository,
            AIService aiService) {
        this.logRepository = logRepository;
        this.aiService = aiService;
    }

    // Retry fallback logs every 30 seconds.
    @Scheduled(fixedDelay = 30000)
    public void retryFallbackLogs() {

        List<Log> fallbackLogs =
                logRepository.findByAiStatus("FALLBACK");

        if (fallbackLogs.isEmpty()) {
            return;
        }

        System.out.println(
                "Checking " + fallbackLogs.size()
                        + " fallback log(s) for AI retry..."
        );

        for (Log log : fallbackLogs) {
            try {
                Map<String, Object> aiResult =
                        aiService.sendLogToAI(log);

                String aiStatus =
                        (String) aiResult.get("aiStatus");

                // AIService returns FALLBACK if Python is unavailable.
                // Keep this log eligible for a future retry.
                if ("FALLBACK".equals(aiStatus)) {
                    System.out.println(
                            "AI still unavailable; log ID "
                                    + log.getId()
                                    + " remains FALLBACK."
                    );
                    continue;
                }

                Boolean anomaly =
                        (Boolean) aiResult.get("anomaly");

                String risk =
                        (String) aiResult.get("risk");

                log.setAiStatus(
                        Boolean.TRUE.equals(anomaly)
                                ? "ANOMALY"
                                : "NORMAL"
                );

                if (risk != null && !risk.isBlank()) {
                    log.setLabel(risk);
                }

                logRepository.save(log);

                System.out.println(
                        "AI retry successful for log ID: "
                                + log.getId()
                );

            } catch (Exception e) {
                System.out.println(
                        "AI retry failed for log ID "
                                + log.getId() + ": " + e.getMessage()
                );
            }
        }
    }
}
