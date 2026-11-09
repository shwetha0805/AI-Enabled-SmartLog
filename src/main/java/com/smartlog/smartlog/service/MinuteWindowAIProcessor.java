
package com.smartlog.smartlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.smartlog.smartlog.model.WindowPrediction;
import com.smartlog.smartlog.repository.WindowPredictionRepository;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MinuteWindowAIProcessor {

    private static final String ES_URL =
            "http://localhost:9200/smartlog-windows/_search";

    private final WindowFeatureAggregator aggregator;
    private final WindowAIService windowAIService;
    private final ObjectMapper mapper = new ObjectMapper();
private final WindowPredictionRepository predictionRepository;

    private final Set<String> processedWindows =
            Collections.synchronizedSet(new HashSet<>());

    public MinuteWindowAIProcessor(
        WindowFeatureAggregator aggregator,
        WindowAIService windowAIService,
        WindowPredictionRepository predictionRepository) {

    this.aggregator = aggregator;
    this.windowAIService = windowAIService;
    this.predictionRepository = predictionRepository;
}

    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void processCompletedMinute() {

        Instant minuteStart = Instant.now()
                .truncatedTo(ChronoUnit.MINUTES)
                .minus(1, ChronoUnit.MINUTES);

        Instant minuteEnd = minuteStart.plus(1, ChronoUnit.MINUTES);

        String windowKey = minuteStart.toString();
    try {
    if (processedWindows.contains(windowKey)
            || predictionRepository.existsByWindowStart(minuteStart)) {

        processedWindows.add(windowKey);
        return;
        }
    } catch (Exception e) {
    System.err.println("WINDOW AI DATABASE CHECK ERROR | " + e.getMessage());
    return;
    }

        if (processedWindows.contains(windowKey)) {
            return;
        }

        try {

            List<JsonNode> events =
                    fetchMinuteEvents(minuteStart, minuteEnd);

            if (events.isEmpty()) {
                processedWindows.add(windowKey);
                System.out.println(
                        "WINDOW AI | No events in " + windowKey
                );
                return;
            }

            Map<String, Object> features =
                    aggregator.aggregate(events);

            JsonNode result =
                    windowAIService.analyzeWindow(features);

            System.out.println(
                    "===== MINUTE WINDOW AI RESULT ====="
            );

            System.out.println("Window: " + windowKey);
            System.out.println("Events: " + events.size());
            System.out.println(
                    "Prediction: " + result.path("prediction").asText()
            );
            System.out.println(
                    "Anomaly score: " + result.path("anomalyScore").asText()
            );
            System.out.println(
                    "Model: " + result.path("model").asText()
            );

        WindowPrediction prediction = new WindowPrediction();

prediction.setWindowStart(minuteStart);
prediction.setTotalEvents(events.size());
prediction.setPrediction(
        result.path("prediction").asText()
);
prediction.setAnomalyScore(
        result.path("anomalyScore").asDouble()
);
prediction.setModel(
        result.path("model").asText()
);
prediction.setFeatureCount(
        result.path("featureCount").asInt()
);

predictionRepository.save(prediction);

System.out.println("WINDOW AI | Prediction saved to MySQL");
            processedWindows.add(windowKey);

            // Keep only recent window keys in memory.
            if (processedWindows.size() > 180) {
                processedWindows.clear();
                processedWindows.add(windowKey);
            }

        } catch (Exception e) {

            System.err.println(
                    "WINDOW AI ERROR | " + e.getMessage()
            );
        }
    }

    private List<JsonNode> fetchMinuteEvents(
            Instant start,
            Instant end) throws Exception {

        List<JsonNode> events = new ArrayList<>();

        String startTime =
                DateTimeFormatter.ISO_INSTANT.format(start);

        String endTime =
                DateTimeFormatter.ISO_INSTANT.format(end);

        String requestBody = """
            {
              "size": 5000,
              "sort": ["_doc"],
              "track_total_hits": true,
              "query": {
                "range": {
                  "@timestamp": {
                    "gte": "%s",
                    "lt": "%s"
                  }
                }
              }
            }
            """.formatted(startTime, endTime);

        String response = sendRequest(
                ES_URL + "?scroll=1m",
                requestBody
        );

        JsonNode root = mapper.readTree(response);

        String scrollId =
                root.path("_scroll_id").asText();

        try {

            while (true) {

                JsonNode hits =
                        root.path("hits").path("hits");

                if (!hits.isArray() || hits.isEmpty()) {
                    break;
                }

                for (JsonNode hit : hits) {
                    JsonNode source = hit.path("_source");

                    if (!source.isMissingNode()) {
                        events.add(source);
                    }
                }

                if (scrollId.isBlank()) {
                    break;
                }

                String scrollBody = mapper.writeValueAsString(
                        Map.of(
                                "scroll", "1m",
                                "scroll_id", scrollId
                        )
                );

                response = sendRequest(
                        "http://localhost:9200/_search/scroll",
                        scrollBody
                );

                root = mapper.readTree(response);

                scrollId = root.path("_scroll_id").asText();
            }

        } finally {

            if (!scrollId.isBlank()) {
                clearScroll(scrollId);
            }
        }

        return events;
    }

    private String sendRequest(
            String urlString,
            String body) throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection) new URL(urlString).openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(60000);

        connection.setRequestProperty(
                "Content-Type", "application/json"
        );

        try (var output = connection.getOutputStream()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();

        var stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

        String response;

        try (var input = stream) {
            response = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        connection.disconnect();

        if (status < 200 || status >= 300) {
            throw new RuntimeException(
                    "Elasticsearch HTTP " + status + ": " + response
            );
        }

        return response;
    }

    private void clearScroll(String scrollId) {

        try {

            String body = mapper.writeValueAsString(
                    Map.of("scroll_id", List.of(scrollId))
            );

            sendRequest(
                    "http://localhost:9200/_search/scroll",
                    body
            );

        } catch (Exception e) {
            System.err.println(
                    "Could not clear Elasticsearch scroll: "
                            + e.getMessage()
            );
        }
    }
}
