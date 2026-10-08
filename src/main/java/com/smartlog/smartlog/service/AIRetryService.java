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

    // Retry fallback logs every 30 seconds
    @Scheduled(fixedDelay = 30000)
    public void retryFallbackLogs() {

        List<Log> fallbackLogs =
                logRepository.findByAiStatus("FALLBACK");

        if (fallbackLogs.isEmpty()) {
            return;
        }

        System.out.println(
                "🔄 Checking " +
                        fallbackLogs.size() +
                        " fallback log(s) for AI retry..."
        );

        for (Log log : fallbackLogs) {

            try {

                Map<String, Object> aiResult =
                        aiService.sendLogToAI(log);

                Boolean anomaly =
                        (Boolean) aiResult.get("anomaly");

                String risk =
                        (String) aiResult.get("risk");

                if (Boolean.TRUE.equals(anomaly)) {
                    log.setAiStatus("ANOMALY");
                } else {
                    log.setAiStatus("NORMAL");
                }

                log.setLabel(risk);

                logRepository.save(log);

                System.out.println(
                        "✅ AI retry successful for log ID: "
                                + log.getId()
                );

            } catch (Exception e) {

                System.out.println(
                        "⚠️ AI still unavailable. "
                                + "Log ID "
                                + log.getId()
                                + " remains FALLBACK."
                );
            }
        }
    }
}