package com.smartlog.smartlog.controller;

import jakarta.persistence.EntityManager;
import org.springframework.web.bind.annotation.*;

import javax.net.ssl.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
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
                checkService(
                        aiUrl.replace("/analyze", "/health")
                )
        );

        // Elasticsearch
        health.put(
                "elasticsearch",
                checkElasticsearch()
        );

        // Logstash
        health.put(
                "logstash",
                checkService(
                        "http://100.94.231.105:9600"
                )
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

        try {

            URL url = new URL(serviceUrl);

            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");

            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            int responseCode =
                    connection.getResponseCode();

            connection.disconnect();

            if (responseCode >= 200 &&
                    responseCode < 500) {

                return "ONLINE";
            }

        } catch (Exception ignored) {
        }

        return "OFFLINE";
    }

    private String checkElasticsearch() {

        try {

            // Accept Elasticsearch's self-signed certificate
            TrustManager[] trustAllCertificates =
                    new TrustManager[]{
                            new X509TrustManager() {

                                @Override
                                public X509Certificate[]
                                getAcceptedIssuers() {

                                    return new X509Certificate[0];
                                }

                                @Override
                                public void checkClientTrusted(
                                        X509Certificate[] certs,
                                        String authType) {
                                }

                                @Override
                                public void checkServerTrusted(
                                        X509Certificate[] certs,
                                        String authType) {
                                }
                            }
                    };

            SSLContext sslContext =
                    SSLContext.getInstance("TLS");

            sslContext.init(
                    null,
                    trustAllCertificates,
                    new java.security.SecureRandom()
            );

            URL url =
                    new URL(
                            "https://100.94.231.105:9200"
                    );

            HttpsURLConnection connection =
                    (HttpsURLConnection)
                            url.openConnection();

            connection.setSSLSocketFactory(
                    sslContext.getSocketFactory()
            );

            connection.setHostnameVerifier(
                    (hostname, session) -> true
            );

            connection.setRequestMethod("GET");

            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            // Elasticsearch authentication
            String username = "elastic";

            String password =
                    System.getenv(
                            "SMARTLOG_ES_PASSWORD"
                    );

            if (password == null ||
                    password.isBlank()) {

                connection.disconnect();

                return "OFFLINE";
            }

            String credentials =
                    username + ":" + password;

            String encodedCredentials =
                    java.util.Base64
                            .getEncoder()
                            .encodeToString(
                                    credentials.getBytes(
                                            java.nio.charset.StandardCharsets.UTF_8
                                    )
                            );

            connection.setRequestProperty(
                    "Authorization",
                    "Basic " + encodedCredentials
            );

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            int responseCode =
                    connection.getResponseCode();

            connection.disconnect();

            if (responseCode >= 200 &&
                    responseCode < 500) {

                return "ONLINE";
            }

        } catch (Exception ignored) {
        }

        return "OFFLINE";
    }
}