package com.tradingbot.production.repository;

import com.tradingbot.production.model.AlgorithmInstanceDocument;
import com.tradingbot.production.model.AlgorithmStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for algorithm instances
 */
@Repository
public interface AlgorithmInstanceRepository extends MongoRepository<AlgorithmInstanceDocument, String> {

    /**
     * Find all instances with given status
     */
    List<AlgorithmInstanceDocument> findByStatus(AlgorithmStatus status);

    /**
     * Find instance by name
     */
    Optional<AlgorithmInstanceDocument> findByName(String name);

    /**
     * Count instances with given status
     */
    long countByStatus(AlgorithmStatus status);

    /**
     * Check if instance with given name exists
     */
    boolean existsByName(String name);
}
