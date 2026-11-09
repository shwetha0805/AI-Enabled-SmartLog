package com.smartlog.smartlog.repository;

import com.smartlog.smartlog.model.WindowPrediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface WindowPredictionRepository
        extends JpaRepository<WindowPrediction, Long> {

    boolean existsByWindowStart(Instant windowStart);

    List<WindowPrediction> findTop50ByOrderByWindowStartDesc();
}