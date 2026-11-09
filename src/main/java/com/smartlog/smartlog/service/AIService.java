
package com.smartlog.smartlog.service;

import com.smartlog.smartlog.model.Log;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AIService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final FallbackAIService fallbackAIService;

    private final String AI_URL = System.getenv().getOrDefault(
            "SMARTLOG_AI_URL",
            "http://127.0.0.1:5000/analyze"
    );

    public AIService(FallbackAIService fallbackAIService) {
        this.fallbackAIService = fallbackAIService;
    }

    public Map<String, Object> sendLogToAI(Log log) {

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("severity", valueOrDefault(log.getSeverity(), "LOW"));
            payload.put("eventType", valueOrDefault(log.getEventType(), "UNKNOWN"));
            payload.put("eventCode", valueOrDefault(log.getEventCode(), "UNKNOWN"));
            payload.put("message", valueOrDefault(log.getMessage(), ""));

            // These fields are not stored in the current Log entity.
            // Python will use its documented defaults for now.
            payload.put("outcome", "UNKNOWN");
            payload.put("channel", "Security");
            payload.put("logonType", "UNKNOWN");

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    AI_URL,
                    request,
                    Map.class
            );

            Map<String, Object> body = response.getBody();

            if (body == null) {
                throw new IllegalStateException(
                        "AI service returned an empty response"
                );
            }

            Map<String, Object> result = new LinkedHashMap<>(body);
            result.put("aiStatus", "ONLINE");
            return result;

        } catch (Exception e) {
            System.out.println(
                    "AI service unavailable. Using rule-based fallback: "
                            + e.getMessage()
            );

            fallbackAIService.analyze(log);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("anomaly", "ANOMALY".equals(log.getLabel()));
            result.put("risk", log.getLabel());
            result.put("label", log.getLabel());
            result.put("aiStatus", "FALLBACK");
            result.put(
                    "reason",
                    "Python AI unavailable; rule-based fallback used"
            );
            result.put("model", "Rule-based fallback");

            return result;
        }
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
