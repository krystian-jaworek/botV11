package com.tradingbot.persistence.repositories;

import com.tradingbot.persistence.entities.SimulationResultDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository for simulation results.
 * Note: Collection name is set dynamically based on algorithm and trading pair.
 */
@Repository
public interface SimulationResultRepository extends MongoRepository<SimulationResultDocument, String> {

    /**
     * Find all non-interrupted simulations ordered by profit percentage descending
     */
    @Query("{'interrupted': false}")
    List<SimulationResultDocument> findAllSuccessful();

    /**
     * Find all interrupted simulations
     */
    @Query("{'interrupted': true}")
    List<SimulationResultDocument> findAllInterrupted();

    /**
     * Find best result by profit percentage
     */
    SimulationResultDocument findFirstByInterruptedFalseOrderByProfitPercentageDesc();

    /**
     * Find worst result by profit percentage (among successful)
     */
    SimulationResultDocument findFirstByInterruptedFalseOrderByProfitPercentageAsc();

    /**
     * Find results with profit above threshold
     */
    List<SimulationResultDocument> findByInterruptedFalseAndProfitPercentageGreaterThanEqualOrderByProfitPercentageDesc(
        BigDecimal minProfitPercentage
    );

    /**
     * Find results with drawdown below threshold
     */
    List<SimulationResultDocument> findByInterruptedFalseAndMaxPortfolioDrawdownPercentageLessThanEqualOrderByProfitPercentageDesc(
        BigDecimal maxDrawdownPercentage
    );

    /**
     * Count successful simulations
     */
    long countByInterruptedFalse();

    /**
     * Count interrupted simulations
     */
    long countByInterruptedTrue();
}
