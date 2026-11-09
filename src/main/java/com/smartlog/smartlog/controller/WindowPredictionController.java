package com.smartlog.smartlog.controller;

import com.smartlog.smartlog.model.WindowPrediction;
import com.smartlog.smartlog.repository.WindowPredictionRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/window-predictions")
@CrossOrigin(origins = "*")
public class WindowPredictionController {

    private final WindowPredictionRepository repository;

    public WindowPredictionController(
            WindowPredictionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<WindowPrediction> getLatestPredictions() {
        return repository.findTop50ByOrderByWindowStartDesc();
    }
}