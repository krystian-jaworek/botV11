package com.tradingbot.persistence.repositories;

import com.tradingbot.persistence.entities.AlgorithmStateDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for algorithm states (production system).
 */
@Repository
public interface AlgorithmStateRepository extends MongoRepository<AlgorithmStateDocument, String> {

    /**
     * Find by algorithm ID
     */
    Optional<AlgorithmStateDocument> findByAlgorithmId(String algorithmId);

    /**
     * Find all active algorithms
     */
    List<AlgorithmStateDocument> findByActiveTrue();

    /**
     * Find by algorithm name and trading pair
     */
    List<AlgorithmStateDocument> findByAlgorithmNameAndTradingPair(String algorithmName, String tradingPair);

    /**
     * Find active algorithms for a trading pair
     */
    List<AlgorithmStateDocument> findByTradingPairAndActiveTrue(String tradingPair);

    /**
     * Check if algorithm exists
     */
    boolean existsByAlgorithmId(String algorithmId);

    /**
     * Delete inactive algorithms older than timestamp
     */
    void deleteByActiveFalseAndLastUpdateTimestampLessThan(long timestamp);
}
