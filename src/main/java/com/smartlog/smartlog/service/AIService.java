package com.smartlog.smartlog.service;

import com.smartlog.smartlog.model.Log;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class AIService {

    private final RestTemplate restTemplate = new RestTemplate();

    private final String AI_URL =
            System.getenv().getOrDefault(
                    "SMARTLOG_AI_URL",
                    "http://127.0.0.1:5000/analyze"
            );



    public Map<String, Object> sendLogToAI(Log log) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Log> request =
                new HttpEntity<>(log, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        AI_URL,
                        request,
                        Map.class
                );

        return response.getBody();
    }
}