
package com.smartlog.smartlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class WindowFeatureAggregator {

    private static final List<String> IMPORTANT_CODES = List.of(
            "4907", "5379", "11926", "600", "4624",
            "4672", "4104", "225", "16", "4799",
            "1001", "19", "400", "1004", "6"
    );

    public Map<String, Object> aggregate(List<JsonNode> events) {

        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot aggregate an empty event window"
            );
        }

        List<String> codes = new ArrayList<>();
        List<String> levels = new ArrayList<>();
        Set<String> processes = new HashSet<>();

        for (JsonNode event : events) {

            String code = getNestedText(event, "event", "code");

            if (code == null) {
                code = getNestedText(event, "winlog", "event_id");
            }

            if (code == null) {
                code = "";
            }

            codes.add(code);

            String level = getNestedText(event, "log", "level");

            levels.add(level == null ? "" : level.toLowerCase());

            String process = getNestedText(
                    event,
                    "winlog",
                    "event_data",
                    "ProcessName"
            );

            if (process != null) {

                String normalized = process
                        .replace("/", "\\")
                        .toLowerCase();

                int lastSeparator = normalized.lastIndexOf('\\');

                String processName = normalized.substring(
                        lastSeparator + 1
                );

                if (!processName.isBlank()) {
                    processes.add(processName);
                }
            }
        }

        Map<String, Object> features = new LinkedHashMap<>();

        features.put("total_events", events.size());
        features.put("unique_event_codes", new HashSet<>(codes).size());

        features.put(
                "warning_count",
                Collections.frequency(levels, "warning")
        );

        features.put(
                "error_count",
                Collections.frequency(levels, "error")
        );

        features.put("unique_processes", processes.size());

        // Event-code counts.
        for (String code : IMPORTANT_CODES) {

            features.put(
                    "event_" + code + "_count",
                    Collections.frequency(codes, code)
            );
        }

        // Event-code ratios.
        for (String code : IMPORTANT_CODES) {

            double ratio =
                    (double) Collections.frequency(codes, code)
                            / events.size();

            features.put("event_" + code + "_ratio", ratio);
        }

        return features;
    }

    private String getNestedText(
            JsonNode node,
            String... fields
    ) {

        JsonNode current = node;

        for (String field : fields) {

            if (current == null ||
                    current.isMissingNode() ||
                    current.isNull()) {
                return null;
            }

            current = current.path(field);
        }

        if (current == null ||
                current.isMissingNode() ||
                current.isNull()) {
            return null;
        }

        String value = current.asText("").trim();

        return value.isEmpty() ? null : value;
    }
}
