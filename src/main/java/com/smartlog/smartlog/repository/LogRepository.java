package com.smartlog.smartlog.repository;

import com.smartlog.smartlog.model.Log;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LogRepository extends JpaRepository<Log, Long> {

    boolean existsByElasticsearchId(String elasticsearchId);

    List<Log> findByAiStatus(String aiStatus);
}