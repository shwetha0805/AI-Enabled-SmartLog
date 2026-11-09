import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class SmartLogIntegrationBenchmark {

    public static void main(String[] args) {

        RestTemplate restTemplate = new RestTemplate();
        String url = "http://127.0.0.1:5000/analyze";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("severity", "HIGH");
        payload.put("eventType", "LOGIN_FAILED");
        payload.put("eventCode", "4625");
        payload.put("message", "Failed login attempt");
        payload.put("outcome", "UNKNOWN");
        payload.put("channel", "Security");
        payload.put("logonType", "UNKNOWN");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(payload, headers);

        List<Double> latencies = new ArrayList<>();

        int warmup = 20;
        int runs = 1000;
        int successful = 0;
        int failed = 0;

        System.out.println("Warming up Java-to-Flask integration...");

        for (int i = 0; i < warmup; i++) {
            restTemplate.postForEntity(url, request, Map.class);
        }

        System.out.println("Running " + runs + " measured requests...");

        long totalStart = System.nanoTime();

        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();

            try {
                ResponseEntity<Map> response =
                        restTemplate.postForEntity(url, request, Map.class);

                if (response.getStatusCode().is2xxSuccessful()
                        && response.getBody() != null) {
                    successful++;
                    latencies.add(
                        (System.nanoTime() - start) / 1_000_000.0
                    );
                } else {
                    failed++;
                }

            } catch (Exception e) {
                failed++;
            }
        }

        double totalSeconds =
                (System.nanoTime() - totalStart) / 1_000_000_000.0;

        Collections.sort(latencies);

        if (!latencies.isEmpty()) {
            double average = latencies.stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0);

            double median = latencies.get(latencies.size() / 2);
            double p95 = latencies.get(
                    (int) Math.ceil(latencies.size() * 0.95) - 1
            );

            System.out.println("\n===== SMARTLOG JAVA INTEGRATION BENCHMARK =====");
            System.out.println("Total measured requests: " + runs);
            System.out.println("Warm-up requests: " + warmup);
            System.out.println("Successful requests: " + successful);
            System.out.println("Failed requests: " + failed);
            System.out.printf("Average latency: %.3f ms%n", average);
            System.out.printf("Median latency: %.3f ms%n", median);
            System.out.printf("P95 latency: %.3f ms%n", p95);
            System.out.printf("Throughput: %.2f requests/second%n",
                    successful / totalSeconds);
            System.out.printf("Total duration: %.3f seconds%n",
                    totalSeconds);
        }
    }
}
