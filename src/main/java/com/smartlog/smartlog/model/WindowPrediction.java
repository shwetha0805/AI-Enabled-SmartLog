package com.smartlog.smartlog.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "window_predictions")
public class WindowPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Instant windowStart;

    private int totalEvents;

    @Column(nullable = false)
    private String prediction;

    private double anomalyScore;

    private String model;

    private int featureCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WindowPrediction() {
    }

    public Long getId() {
        return id;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public int getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(int totalEvents) {
        this.totalEvents = totalEvents;
    }

    public String getPrediction() {
        return prediction;
    }

    public void setPrediction(String prediction) {
        this.prediction = prediction;
    }

    public double getAnomalyScore() {
        return anomalyScore;
    }

    public void setAnomalyScore(double anomalyScore) {
        this.anomalyScore = anomalyScore;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getFeatureCount() {
        return featureCount;
    }

    public void setFeatureCount(int featureCount) {
        this.featureCount = featureCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}