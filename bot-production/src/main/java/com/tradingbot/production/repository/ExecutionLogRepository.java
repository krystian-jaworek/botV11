package com.tradingbot.production.repository;

import com.tradingbot.production.model.ExecutionLogDocument;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * MongoDB repository for execution logs
 */
@Repository
public interface ExecutionLogRepository extends MongoRepository<ExecutionLogDocument, String> {

    /**
     * Find logs for a specific instance, ordered by timestamp descending
     */
    List<ExecutionLogDocument> findByInstanceIdOrderByTimestampDesc(String instanceId, PageRequest pageRequest);

    /**
     * Find logs for a specific instance within time range
     */
    List<ExecutionLogDocument> findByInstanceIdAndTimestampBetween(
        String instanceId,
        Instant start,
        Instant end
    );

    /**
     * Find logs with errors for a specific instance
     */
    List<ExecutionLogDocument> findByInstanceIdAndErrorIsNotNull(String instanceId);

    /**
     * Count logs for a specific instance
     */
    long countByInstanceId(String instanceId);

    /**
     * Delete old logs (cleanup)
     */
    void deleteByTimestampBefore(Instant before);
}
