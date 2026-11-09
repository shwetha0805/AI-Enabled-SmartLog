package com.smartlog.smartlog.controller;

import com.smartlog.smartlog.model.Log;
import com.smartlog.smartlog.repository.LogRepository;
import com.smartlog.smartlog.service.AIService;
import com.smartlog.smartlog.service.ElasticsearchService;
import com.smartlog.smartlog.service.FallbackAIService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogRepository logRepository;
    private final AIService aiService;
    private final ElasticsearchService elasticsearchService;
    private final FallbackAIService fallbackAIService;

    public LogController(
            LogRepository logRepository,
            AIService aiService,
            ElasticsearchService elasticsearchService,
            FallbackAIService fallbackAIService) {

        this.logRepository = logRepository;
        this.aiService = aiService;
        this.elasticsearchService = elasticsearchService;
        this.fallbackAIService = fallbackAIService;
    }

    // CREATE LOG + AI ANALYSIS


    @PostMapping
public Log createLog(@Valid @RequestBody Log log) {

    Map<String, Object> aiResult =
            aiService.sendLogToAI(log);

    Boolean anomaly = (Boolean) aiResult.get("anomaly");
    String risk = (String) aiResult.get("risk");
    String aiStatus = (String) aiResult.get("aiStatus");

    // Save AI prediction details
    log.setAiModel((String) aiResult.get("model"));
    log.setAiReason((String) aiResult.get("reason"));

    Object score = aiResult.get("anomalyScore");

    if (score instanceof Number) {
        log.setAnomalyScore(((Number) score).doubleValue());
    }

    if (risk != null) {
        log.setLabel(risk);
    }

    if ("FALLBACK".equals(aiStatus)) {
        log.setAiStatus("FALLBACK");
    } else if (Boolean.TRUE.equals(anomaly)) {
        log.setAiStatus("ANOMALY");
    } else {
        log.setAiStatus("NORMAL");
    }

    return logRepository.save(log);
}


    // GET ALL LOGS
    @GetMapping
    public List<Log> getAllLogs() {
        return logRepository.findAll();
    }

    // TEST ELASTICSEARCH CONNECTION
    @GetMapping("/elasticsearch-test")
    public ResponseEntity<String> testElasticsearch() {

        try {

            String result = elasticsearchService.getOneLog();

            return ResponseEntity.ok(result);

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Elasticsearch is currently unavailable.");
        }
    }

    @GetMapping("/process-real-log")
    public Log processRealLog() throws Exception {
        return elasticsearchService.processLatestRealLog();
    }

    // Get ID mapping
    @GetMapping("/{id}")
    public ResponseEntity<?> getLogById(@PathVariable Long id) {

        if (logRepository.findById(id).isPresent()) {

            Log log = logRepository.findById(id).get();

            return ResponseEntity.ok(log);
        }

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Log not found with ID: " + id);
    }

    // UPDATE LOG
    @PutMapping("/{id}")
    public Log updateLog(
            @PathVariable Long id,
            @RequestBody Log newLog) {

        return logRepository.findById(id).map(log -> {

            log.setTimestamp(newLog.getTimestamp());
            log.setUsername(newLog.getUsername());
            log.setEventType(newLog.getEventType());
            log.setEventCode(newLog.getEventCode());
            log.setSourceIp(newLog.getSourceIp());
            log.setSeverity(newLog.getSeverity());
            log.setMessage(newLog.getMessage());

            // Update AI-related fields if provided
            if (newLog.getAiStatus() != null) {
                log.setAiStatus(newLog.getAiStatus());
            }

            if (newLog.getLabel() != null) {
                log.setLabel(newLog.getLabel());
            }

            return logRepository.save(log);

        }).orElse(null);
    }

    // RETRY AI ANALYSIS FOR FALLBACK LOG

    @PostMapping("/{id}/retry-ai")
    public Log retryAI(@PathVariable Long id) {

        return logRepository.findById(id).map(log -> {

            try {

                Map<String, Object> aiResult =
                        aiService.sendLogToAI(log);

                String serviceStatus =
                        (String) aiResult.get("aiStatus");

                // Keep the existing status if Python AI is unavailable.
                if ("FALLBACK".equals(serviceStatus)) {

                    log.setAiStatus("FALLBACK");

                    System.out.println(
                            "AI still unavailable. Log ID "
                                    + id + " remains FALLBACK."
                    );

                    return logRepository.save(log);
                }

                Boolean anomaly =
                        (Boolean) aiResult.get("anomaly");

                String risk =
                        (String) aiResult.get("risk");

                if (Boolean.TRUE.equals(anomaly)) {
                    log.setAiStatus("ANOMALY");
                } else {
                    log.setAiStatus("NORMAL");
                }

                if (risk != null && !risk.isBlank()) {
                    log.setLabel(risk);
                }

                return logRepository.save(log);

            } catch (Exception e) {

                System.out.println(
                        "AI retry failed for log ID "
                                + id + ": " + e.getMessage()
                );

                // Do not change the saved log on an exception.
                return log;
            }

        }).orElse(null);
    }


    // DELETE LOG
    @DeleteMapping("/{id}")
    public String deleteLog(@PathVariable Long id) {

        if (logRepository.existsById(id)) {
            logRepository.deleteById(id);
            return "Log deleted successfully";
        }

        return "Log not found";
    }
}