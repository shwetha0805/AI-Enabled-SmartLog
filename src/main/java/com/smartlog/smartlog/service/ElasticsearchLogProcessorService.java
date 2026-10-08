package com.smartlog.smartlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlog.smartlog.model.Log;
import com.smartlog.smartlog.repository.LogRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class ElasticsearchLogProcessorService {

    private final LogRepository logRepository;
    private final AIService aiService;
    private final FallbackAIService fallbackAIService;

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final String ES_URL =
            "https://100.94.231.105:9200/smartlog-windows/_search";

    private final String USERNAME = "elastic";

    private final String PASSWORD =
            System.getenv("SMARTLOG_ES_PASSWORD");

    public ElasticsearchLogProcessorService(
            LogRepository logRepository,
            AIService aiService,
            FallbackAIService fallbackAIService) {

        this.logRepository = logRepository;
        this.aiService = aiService;
        this.fallbackAIService = fallbackAIService;
    }

    /*
     * Check Elasticsearch every 15 seconds.
     */
    @Scheduled(fixedDelay = 15000)
    public void processNewLogs() {

        try {

            String response = getRecentLogs();

            JsonNode root =
                    objectMapper.readTree(response);

            JsonNode hits =
                    root.path("hits").path("hits");

            if (!hits.isArray()) {
                return;
            }

            System.out.println(
                    "🔎 Elasticsearch returned "
                            + hits.size()
                            + " recent event(s)."
            );

            for (JsonNode hit : hits) {

                /*
                 * Elasticsearch document ID.
                 * Used to prevent duplicate MySQL entries.
                 */
                String elasticsearchId =
                        hit.path("_id").asText();

                if (elasticsearchId == null ||
                        elasticsearchId.isBlank()) {

                    continue;
                }

                /*
                 * Skip events already stored in MySQL.
                 */
                if (logRepository.existsByElasticsearchId(
                        elasticsearchId)) {

                    continue;
                }

                JsonNode source =
                        hit.path("_source");

                Log log =
                        convertToLog(
                                source,
                                elasticsearchId
                        );

                if (log == null) {
                    continue;
                }

                /*
                 * Send event to AI.
                 */
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

                    if (risk != null &&
                            !risk.isBlank()) {

                        log.setLabel(risk);

                    } else {

                        log.setLabel("LOW");
                    }

                    System.out.println(
                            "🤖 REAL AI | Event: "
                                    + log.getEventCode()
                                    + " | "
                                    + log.getAiStatus()
                                    + " | Risk: "
                                    + log.getLabel()
                    );

                } catch (Exception aiError) {

                    /*
                     * AI unavailable → fallback.
                     */
                    System.out.println(
                            "⚠️ AI unavailable. "
                                    + "Using fallback."
                    );

                    log =
                            fallbackAIService.analyze(log);
                }

                /*
                 * Save processed event to MySQL.
                 */
                logRepository.save(log);

                System.out.println(
                        "✅ NEW WINDOWS LOG SAVED"
                                + " | ES ID: "
                                + elasticsearchId
                                + " | Event: "
                                + log.getEventCode()
                                + " | User: "
                                + log.getUsername()
                                + " | Severity: "
                                + log.getSeverity()
                                + " | AI: "
                                + log.getAiStatus()
                                + " | Risk: "
                                + log.getLabel()
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "⚠️ Automatic Elasticsearch processing error: "
                            + e.getMessage()
            );
        }
    }

    /*
     * Convert a real Elasticsearch event into
     * the SmartLog Log entity.
     */
    private Log convertToLog(
            JsonNode source,
            String elasticsearchId) {

        try {

            Log log = new Log();

            /*
             * Store Elasticsearch document ID.
             */
            log.setElasticsearchId(
                    elasticsearchId
            );

            /*
             * Timestamp.
             */
            String timestamp =
                    getText(
                            source,
                            "@timestamp"
                    );

            if (timestamp != null) {

                try {

                    log.setTimestamp(
                            java.time.OffsetDateTime
                                    .parse(timestamp)
                                    .toLocalDateTime()
                    );

                } catch (Exception e) {

                    log.setTimestamp(
                            java.time.LocalDateTime.now()
                    );
                }

            } else {

                log.setTimestamp(
                        java.time.LocalDateTime.now()
                );
            }

            /*
             * Username.
             */
            String username =
                    getNestedText(
                            source,
                            "winlog",
                            "event_data",
                            "TargetUserName"
                    );

            if (isBlank(username)) {

                username =
                        getNestedText(
                                source,
                                "user",
                                "name"
                        );
            }

            if (isBlank(username)) {

                username = "UNKNOWN";
            }

            log.setUsername(username);

            /*
             * Event code.
             *
             * Example:
             * 4625 = failed logon
             * 4624 = successful logon
             * 4798 = user account group enumeration
             */
            String eventCode =
                    getNestedText(
                            source,
                            "event",
                            "code"
                    );

            if (isBlank(eventCode)) {

                eventCode =
                        getNestedText(
                                source,
                                "winlog",
                                "event_id"
                        );
            }

            if (isBlank(eventCode)) {

                eventCode = "UNKNOWN";
            }

            log.setEventCode(eventCode);

            /*
             * Event type/action.
             */
            String eventType =
                    getNestedText(
                            source,
                            "event",
                            "action"
                    );

            if (isBlank(eventType)) {

                eventType =
                        getNestedText(
                                source,
                                "event",
                                "kind"
                        );
            }

            if (isBlank(eventType)) {

                eventType =
                        "WINDOWS_EVENT_"
                                + eventCode;
            }

            log.setEventType(eventType);

            /*
             * Message.
             */
            String message =
                    getText(
                            source,
                            "message"
                    );

            if (isBlank(message)) {

                message =
                        "No event message available";
            }

            log.setMessage(message);

            /*
             * Source IP.
             */
            String sourceIp =
                    getNestedText(
                            source,
                            "source",
                            "ip"
                    );

            if (isBlank(sourceIp)) {

                sourceIp =
                        getNestedText(
                                source,
                                "source",
                                "address"
                        );
            }

            /*
             * Windows Security events such as
             * 4625 store the IP here.
             */
            if (isBlank(sourceIp)) {

                sourceIp =
                        getNestedText(
                                source,
                                "winlog",
                                "event_data",
                                "IpAddress"
                        );
            }

            if (isBlank(sourceIp)) {

                sourceIp = "UNKNOWN";
            }

            log.setSourceIp(sourceIp);

            /*
             * Determine severity.
             */
            String severity =
                    determineSeverity(
                            eventCode,
                            eventType,
                            message
                    );

            log.setSeverity(severity);

            return log;

        } catch (Exception e) {

            System.out.println(
                    "⚠️ Could not convert Elasticsearch event: "
                            + e.getMessage()
            );

            return null;
        }
    }

    /*
     * SmartLog severity classification.
     */
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

        /*
         * Windows failed login.
         */
        if ("4625".equals(eventCode)) {

            return "HIGH";
        }

        /*
         * Other failed authentication.
         */
        if (eventType.contains("FAILED")) {

            return "HIGH";
        }

        /*
         * Unauthorized activity.
         */
        if (eventType.contains("UNAUTHORIZED")) {

            return "CRITICAL";
        }

        /*
         * Suspicious activity.
         */
        if (eventType.contains("SUSPICIOUS") ||
                message.contains("SUSPICIOUS")) {

            return "MEDIUM";
        }

        return "LOW";
    }

    /*
     * Read direct Elasticsearch field.
     */
    private String getText(
            JsonNode node,
            String field) {

        if (node == null ||
                node.isMissingNode() ||
                node.isNull()) {

            return null;
        }

        JsonNode value =
                node.path(field);

        if (value.isMissingNode() ||
                value.isNull()) {

            return null;
        }

        String text =
                value.asText("");

        return isBlank(text)
                ? null
                : text.trim();
    }

    /*
     * Read nested Elasticsearch field.
     */
    private String getNestedText(
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

        return isBlank(value)
                ? null
                : value.trim();
    }

    private boolean isBlank(
            String value) {

        return value == null ||
                value.trim().isEmpty();
    }

    /*
     * Query Elasticsearch using POST.
     *
     * IMPORTANT:
     * Only events from the last 2 minutes are requested.
     *
     * Events are returned oldest → newest.
     */
    private String getRecentLogs()
            throws Exception {

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

        URL url =
                new URL(ES_URL);

        HttpsURLConnection connection =
                (HttpsURLConnection)
                        url.openConnection();

        connection.setRequestMethod("POST");

        connection.setDoOutput(true);

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
                "Content-Type",
                "application/json"
        );

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);

        /*
         * Elasticsearch query:
         *
         * Last 2 minutes
         * Maximum 100 events
         * Oldest first
         */
        String requestBody =
                """
                {
                  "size": 100,
                  "sort": [
                    {
                      "@timestamp": "asc"
                    }
                  ],
                  "query": {
                    "range": {
                      "@timestamp": {
                        "gte": "now-2m"
                      }
                    }
                  }
                }
                """;

        try (OutputStream outputStream =
                     connection.getOutputStream()) {

            outputStream.write(
                    requestBody.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
        }

        int responseCode =
                connection.getResponseCode();

        BufferedReader reader;

        if (responseCode >= 200 &&
                responseCode < 300) {

            reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    connection.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );

        } else {

            reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    connection.getErrorStream(),
                                    StandardCharsets.UTF_8
                            )
                    );
        }

        StringBuilder response =
                new StringBuilder();

        String line;

        while ((line =
                reader.readLine()) != null) {

            response
                    .append(line)
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
}