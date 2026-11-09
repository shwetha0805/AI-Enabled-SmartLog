
package com.smartlog.smartlog.controller;

import jakarta.persistence.EntityManager;
import org.springframework.web.bind.annotation.*;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final EntityManager entityManager;

    public HealthController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @GetMapping
    public Map<String, String> getHealth() {

        Map<String, String> health = new HashMap<>();

        // Spring Boot
        health.put("springBoot", "ONLINE");

        // MySQL
        health.put("mysql", checkMySQL());

        // AI Service
        String aiUrl = System.getenv().getOrDefault(
                "SMARTLOG_AI_URL",
                "http://127.0.0.1:5000/analyze"
        );

        health.put(
                "aiService",
                checkService(aiUrl.replace("/analyze", "/health"))
        );

        // Elasticsearch
        health.put(
                "elasticsearch",
                checkService("http://localhost:9200")
        );

        // Logstash Beats input
        health.put(
                "logstash",
                checkLogstash()
        );

        return health;
    }

    private String checkMySQL() {

        try {
            entityManager
                    .createNativeQuery("SELECT 1")
                    .getSingleResult();

            return "ONLINE";

        } catch (Exception e) {
            return "OFFLINE";
        }
    }

    private String checkService(String serviceUrl) {

        HttpURLConnection connection = null;

        try {
            URL url = new URL(serviceUrl);

            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            int responseCode = connection.getResponseCode();

            if (responseCode >= 200 && responseCode < 500) {
                return "ONLINE";
            }

        } catch (Exception ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return "OFFLINE";
    }

    private String checkLogstash() {

        try (Socket socket = new Socket()) {

            socket.connect(
                    new InetSocketAddress("localhost", 5044),
                    3000
            );

            return "ONLINE";

        } catch (Exception e) {
            return "OFFLINE";
        }
    }
}
