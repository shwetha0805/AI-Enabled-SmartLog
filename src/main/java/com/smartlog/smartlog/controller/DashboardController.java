package com.smartlog.smartlog.controller;

import com.smartlog.smartlog.model.Log;
import com.smartlog.smartlog.repository.LogRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final LogRepository logRepository;

    public DashboardController(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    // Dashboard statistics
    @GetMapping("/stats")
    public Map<String, Object> getStats() {

        List<Log> logs = logRepository.findAll();

        long totalLogs = logs.size();

        long anomalies = logs.stream()
                .filter(log ->
                        "ANOMALY".equalsIgnoreCase(log.getAiStatus())
                                || "ANOMALY".equalsIgnoreCase(log.getLabel()))
                .count();

        long highRisk = logs.stream()
                .filter(log ->
                        "HIGH".equalsIgnoreCase(log.getSeverity())
                                || "CRITICAL".equalsIgnoreCase(log.getSeverity()))
                .count();

        long critical = logs.stream()
                .filter(log ->
                        "CRITICAL".equalsIgnoreCase(log.getSeverity()))
                .count();

        Map<String, Object> stats = new HashMap<>();

        stats.put("totalLogs", totalLogs);
        stats.put("anomalies", anomalies);
        stats.put("highRisk", highRisk);
        stats.put("critical", critical);

        return stats;
    }

    // Get high-risk and critical alerts
    @GetMapping("/alerts")
    public List<Log> getAlerts() {

        return logRepository.findAll()
                .stream()
                .filter(log ->
                        "HIGH".equalsIgnoreCase(log.getSeverity())
                                || "CRITICAL".equalsIgnoreCase(log.getSeverity()))
                .toList();
    }

    // Get logs waiting for AI analysis
    @GetMapping("/pending")
    public List<Log> getPendingLogs() {

        return logRepository.findByAiStatus("FALLBACK");
    }

    // Get recent logs
    @GetMapping("/recent")
    public List<Log> getRecentLogs() {

        return logRepository.findAll()
                .stream()
                .sorted((a, b) -> {

                    if (a.getTimestamp() == null) return 1;
                    if (b.getTimestamp() == null) return -1;

                    return b.getTimestamp()
                            .compareTo(a.getTimestamp());
                })
                .limit(20)
                .toList();
    }
}