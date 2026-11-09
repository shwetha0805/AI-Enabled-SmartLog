package com.smartlog.smartlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class WindowAIService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String AI_WINDOW_URL =
            System.getenv().getOrDefault(
                    "SMARTLOG_WINDOW_AI_URL",
                    "http://127.0.0.1:5000/analyze-window"
            );

    public JsonNode analyzeWindow(Map<String, Object> features)
            throws Exception {

        URL url = new URL(AI_WINDOW_URL);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);

        connection.setRequestProperty(
                "Content-Type", "application/json"
        );

        connection.setRequestProperty(
                "Accept", "application/json"
        );

        byte[] requestBody =
                objectMapper.writeValueAsBytes(features);

        try (OutputStream output = connection.getOutputStream()) {
            output.write(requestBody);
        }

        int status = connection.getResponseCode();

        InputStream responseStream =
                status >= 200 && status < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

        JsonNode response;

        try (InputStream input = responseStream) {
            response = objectMapper.readTree(input);
        }

        connection.disconnect();

        if (status != 200) {
            throw new RuntimeException(
                    "Window AI returned HTTP " + status
                            + ": " + response
            );
        }

        return response;
    }
}