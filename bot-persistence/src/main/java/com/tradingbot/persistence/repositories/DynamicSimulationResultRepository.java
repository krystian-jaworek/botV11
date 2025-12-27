package com.tradingbot.persistence.repositories;

import com.tradingbot.persistence.entities.SimulationResultDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dynamic repository for simulation results with collection name based on algorithm and trading pair.
 * Collection name format: <AlgorithmName>-<TradingPair>
 *
 * This allows easy separation of results by algorithm and pair,
 * and enables dropping old results per algorithm/pair combination.
 */
@Slf4j
@Component
public class DynamicSimulationResultRepository {

    private final MongoTemplate mongoTemplate;

    public DynamicSimulationResultRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Generate collection name from algorithm and trading pair
     */
    public static String getCollectionName(String algorithmName, String tradingPair) {
        return algorithmName + "-" + tradingPair;
    }

    /**
     * Save a single result
     */
    public SimulationResultDocument save(SimulationResultDocument document) {
        String collectionName = getCollectionName(
            document.getAlgorithmName(),
            document.getTradingPair()
        );

        return mongoTemplate.save(document, collectionName);
    }

    /**
     * Save results in batch (more efficient)
     */
    public void saveAll(List<SimulationResultDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }

        // Group by collection name
        var groupedByCollection = documents.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                doc -> getCollectionName(doc.getAlgorithmName(), doc.getTradingPair())
            ));

        // Insert each group into its collection
        groupedByCollection.forEach((collectionName, docs) -> {
            mongoTemplate.insert(docs, collectionName);
            log.debug("Saved {} documents to collection: {}", docs.size(), collectionName);
        });
    }

    /**
     * Find all results for algorithm and pair
     */
    public List<SimulationResultDocument> findAll(String algorithmName, String tradingPair) {
        String collectionName = getCollectionName(algorithmName, tradingPair);
        return mongoTemplate.findAll(SimulationResultDocument.class, collectionName);
    }

    /**
     * Find successful simulations ordered by profit descending
     */
    public List<SimulationResultDocument> findSuccessful(String algorithmName, String tradingPair) {
        String collectionName = getCollectionName(algorithmName, tradingPair);

        Query query = new Query(Criteria.where("interrupted").is(false))
            .with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC,
                "profitPercentage"
            ));

        return mongoTemplate.find(query, SimulationResultDocument.class, collectionName);
    }

    /**
     * Find best result
     */
    public SimulationResultDocument findBest(String algorithmName, String tradingPair) {
        String collectionName = getCollectionName(algorithmName, tradingPair);

        Query query = new Query(Criteria.where("interrupted").is(false))
            .with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC,
                "profitPercentage"
            ))
            .limit(1);

        return mongoTemplate.findOne(query, SimulationResultDocument.class, collectionName);
    }

    /**
     * Find results with profit above threshold
     */
    public List<SimulationResultDocument> findByMinProfit(
        String algorithmName,
        String tradingPair,
        BigDecimal minProfitPercentage
    ) {
        String collectionName = getCollectionName(algorithmName, tradingPair);

        Query query = new Query(
            Criteria.where("interrupted").is(false)
                .and("profitPercentage").gte(minProfitPercentage)
        ).with(org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC,
            "profitPercentage"
        ));

        return mongoTemplate.find(query, SimulationResultDocument.class, collectionName);
    }

    /**
     * Count total simulations
     */
    public long count(String algorithmName, String tradingPair) {
        String collectionName = getCollectionName(algorithmName, tradingPair);
        return mongoTemplate.count(new Query(), collectionName);
    }

    /**
     * Count successful simulations
     */
    public long countSuccessful(String algorithmName, String tradingPair) {
        String collectionName = getCollectionName(algorithmName, tradingPair);
        Query query = new Query(Criteria.where("interrupted").is(false));
        return mongoTemplate.count(query, collectionName);
    }

    /**
     * Delete all results for algorithm and pair
     */
    public void deleteAll(String algorithmName, String tradingPair) {
        String collectionName = getCollectionName(algorithmName, tradingPair);
        mongoTemplate.dropCollection(collectionName);
        log.info("Dropped collection: {}", collectionName);
    }

    /**
     * Check if collection exists
     */
    public boolean collectionExists(String algorithmName, String tradingPair) {
        String collectionName = getCollectionName(algorithmName, tradingPair);
        return mongoTemplate.collectionExists(collectionName);
    }
}
