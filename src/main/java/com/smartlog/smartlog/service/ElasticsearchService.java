package com.smartlog.smartlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlog.smartlog.model.Log;
import com.smartlog.smartlog.repository.LogRepository;

import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;

@Service
public class ElasticsearchService {

    private final String ES_URL =
            "https://100.94.231.105:9200/smartlog-windows/_search?size=20&sort=@timestamp:desc";

    private final String USERNAME = "elastic";

    // Read password from environment variable
    private final String PASSWORD =
            System.getenv("SMARTLOG_ES_PASSWORD");

    private final LogRepository logRepository;
    private final FallbackAIService fallbackAIService;
    private final AIService aiService;

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    public ElasticsearchService(
            LogRepository logRepository,
            FallbackAIService fallbackAIService,
            AIService aiService) {

        this.logRepository = logRepository;
        this.fallbackAIService = fallbackAIService;
        this.aiService = aiService;
    }


    // =====================================================
    // GET RECENT LOGS FROM ELASTICSEARCH
    // =====================================================

    public String getRecentLogs() throws Exception {

        // Prototype testing only
        TrustManager[] trustAllCertificates =
                new TrustManager[]{

                        new X509TrustManager() {

                            @Override
                            public java.security.cert.X509Certificate[]
                            getAcceptedIssuers() {

                                return new java.security.cert.X509Certificate[0];
                            }

                            @Override
                            public void checkClientTrusted(
                                    java.security.cert.X509Certificate[] certs,
                                    String authType) {
                            }

                            @Override
                            public void checkServerTrusted(
                                    java.security.cert.X509Certificate[] certs,
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

        HttpsURLConnection.setDefaultSSLSocketFactory(
                sslContext.getSocketFactory()
        );

        HttpsURLConnection.setDefaultHostnameVerifier(
                (hostname, session) -> true
        );

        URL url = new URL(ES_URL);

        HttpsURLConnection connection =
                (HttpsURLConnection) url.openConnection();

        connection.setRequestMethod("GET");

        String credentials =
                USERNAME + ":" + PASSWORD;

        String encodedCredentials =
                Base64.getEncoder()
                        .encodeToString(
                                credentials.getBytes(
                                        StandardCharsets.UTF_8
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

        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);

        int responseCode =
                connection.getResponseCode();

        BufferedReader reader;

        if (responseCode >= 200 &&
                responseCode < 300) {

            reader = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );

        } else {

            reader = new BufferedReader(
                    new InputStreamReader(
                            connection.getErrorStream(),
                            StandardCharsets.UTF_8
                    )
            );
        }

        StringBuilder response =
                new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {

            response.append(line)
                    .append("\n");
        }

        reader.close();
        connection.disconnect();

        if (responseCode != 200) {

            throw new RuntimeException(
                    "Elasticsearch returned HTTP "
                            + responseCode
                            + ": "
                            + response
            );
        }

        return response.toString();
    }


    // =====================================================
    // ELASTICSEARCH CONNECTION TEST
    // =====================================================

    public String getOneLog() throws Exception {

        return getRecentLogs();
    }


    // =====================================================
    // PROCESS ONE REAL WINDOWS EVENT
    // =====================================================

    public Log processLatestRealLog() throws Exception {

        String response =
                getRecentLogs();

        JsonNode root =
                objectMapper.readTree(response);

        JsonNode hits =
                root.path("hits")
                        .path("hits");

        if (!hits.isArray() ||
                hits.isEmpty()) {

            throw new RuntimeException(
                    "No Elasticsearch logs found."
            );
        }

        JsonNode hit = hits.get(0);

        String elasticsearchId =
                hit.path("_id").asText();

        JsonNode source =
                hit.path("_source");

        Log log = new Log();

        log.setElasticsearchId(elasticsearchId);


        // =================================================
        // TIMESTAMP
        // =================================================

        String timestamp =
                getSafeText(
                        source,
                        "@timestamp",
                        null
                );

        if (timestamp != null) {

            try {

                log.setTimestamp(
                        OffsetDateTime
                                .parse(timestamp)
                                .toLocalDateTime()
                );

            } catch (Exception e) {

                System.out.println(
                        "⚠️ Invalid timestamp: "
                                + timestamp
                );
            }
        }


        // =================================================
        // USERNAME
        // =================================================

        String username =
                getNestedSafeText(
                        source,
                        "winlog",
                        "event_data",
                        "TargetUserName"
                );

        if (isBlank(username)) {

            username =
                    getNestedSafeText(
                            source,
                            "user",
                            "name"
                    );
        }

        if (isBlank(username)) {

            username = "UNKNOWN";
        }

        log.setUsername(username);


        // =================================================
        // EVENT TYPE
        // =================================================

        String eventType =
                getNestedSafeText(
                        source,
                        "event",
                        "action"
                );

        // If event.action is empty,
        // try event.kind
        if (isBlank(eventType)) {

            eventType =
                    getNestedSafeText(
                            source,
                            "event",
                            "kind"
                    );
        }

        // If still empty, use event code
        String eventCode =
                getNestedSafeText(
                        source,
                        "event",
                        "code"
                );

        if (isBlank(eventCode)) {

            eventCode =
                    getNestedSafeText(
                            source,
                            "winlog",
                            "event_id"
                    );
        }

        if (isBlank(eventCode)) {

            eventCode = "UNKNOWN";
        }

        if (isBlank(eventType)) {

            eventType =
                    "WINDOWS_EVENT_" + eventCode;
        }

        log.setEventType(eventType);


        // =================================================
        // EVENT CODE
        // =================================================

        log.setEventCode(eventCode);


        // =================================================
        // MESSAGE
        // =================================================

        String message =
                getSafeText(
                        source,
                        "message",
                        "No event message available"
                );

        if (isBlank(message)) {

            message =
                    "No event message available";
        }

        log.setMessage(message);


        // =================================================
        // SOURCE IP
        // =================================================

        String sourceIp =
                getNestedSafeText(
                        source,
                        "source",
                        "ip"
                );

        if (isBlank(sourceIp)) {

            sourceIp =
                    getNestedSafeText(
                            source,
                            "source",
                            "address"
                    );
        }

        if (isBlank(sourceIp)) {

            sourceIp = "UNKNOWN";
        }

        log.setSourceIp(sourceIp);


        // =================================================
        // SEVERITY
        // =================================================

        String severity =
                determineSeverity(
                        eventCode,
                        eventType,
                        message
                );

        if (isBlank(severity)) {

            severity = "LOW";
        }

        log.setSeverity(severity);


        // =================================================
        // DEBUG INFORMATION
        // =================================================

        System.out.println(
                "======================================"
        );

        System.out.println(
                "REAL WINDOWS EVENT"
        );

        System.out.println(
                "Username : " + log.getUsername()
        );

        System.out.println(
                "Event    : " + log.getEventType()
        );

        System.out.println(
                "Code     : " + log.getEventCode()
        );

        System.out.println(
                "Severity : " + log.getSeverity()
        );

        System.out.println(
                "Source IP: " + log.getSourceIp()
        );

        System.out.println(
                "======================================"
        );


        // =================================================
        // AI ANALYSIS
        // =================================================

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

            log.setLabel(
                    isBlank(risk)
                            ? "LOW"
                            : risk
            );


            System.out.println(
                    "REAL AI | "
                            + log.getAiStatus()
                            + " | Risk: "
                            + log.getLabel()
            );

        } catch (Exception e) {

            System.out.println(
                    "⚠️ AI unavailable. "
                            + "Using fallback analysis."
            );

            log =
                    fallbackAIService.analyze(log);
        }


        // =================================================
        // FINAL SAFETY CHECK
        // =================================================

        if (isBlank(log.getUsername())) {

            log.setUsername("UNKNOWN");
        }

        if (isBlank(log.getEventType())) {

            log.setEventType("UNKNOWN_EVENT");
        }

        if (isBlank(log.getSeverity())) {

            log.setSeverity("LOW");
        }

        if (isBlank(log.getSourceIp())) {

            log.setSourceIp("UNKNOWN");
        }

        if (isBlank(log.getAiStatus())) {

            log.setAiStatus("PENDING");
        }

        if (isBlank(log.getLabel())) {

            log.setLabel("LOW");
        }


        // =================================================
        // SAVE TO MYSQL
        // =================================================

        return logRepository.save(log);
    }


    // =====================================================
    // SEVERITY RULES
    // =====================================================

    private String determineSeverity(
            String eventCode,
            String eventType,
            String message) {

        eventCode =
                eventCode == null
                        ? ""
                        : eventCode.toUpperCase();

        eventType =
                eventType == null
                        ? ""
                        : eventType.toUpperCase();

        message =
                message == null
                        ? ""
                        : message.toUpperCase();


        // Windows failed login
        if ("4625".equals(eventCode)) {

            return "HIGH";
        }


        // Failed authentication
        if (eventType.contains("FAILED")) {

            return "HIGH";
        }


        // Unauthorized activity
        if (eventType.contains("UNAUTHORIZED")) {

            return "CRITICAL";
        }


        // Suspicious activity
        if (eventType.contains("SUSPICIOUS") ||
                message.contains("SUSPICIOUS")) {

            return "MEDIUM";
        }


        return "LOW";
    }


    // =====================================================
    // SAFE JSON TEXT
    // =====================================================

    private String getSafeText(
            JsonNode node,
            String field,
            String defaultValue) {

        if (node == null ||
                node.isMissingNode() ||
                node.isNull()) {

            return defaultValue;
        }

        String value =
                node.path(field)
                        .asText("");

        if (isBlank(value)) {

            return defaultValue;
        }

        return value.trim();
    }


    // =====================================================
    // SAFE NESTED JSON TEXT
    // =====================================================

    private String getNestedSafeText(
            JsonNode node,
            String... fields) {

        JsonNode current = node;

        for (String field : fields) {

            if (current == null ||
                    current.isMissingNode() ||
                    current.isNull()) {

                return null;
            }

            current =
                    current.path(field);
        }

        if (current == null ||
                current.isMissingNode() ||
                current.isNull()) {

            return null;
        }

        String value =
                current.asText("");

        if (isBlank(value)) {

            return null;
        }

        return value.trim();
    }


    // =====================================================
    // CHECK BLANK
    // =====================================================

    private boolean isBlank(String value) {

        return value == null ||
                value.trim().isEmpty();
    }
}