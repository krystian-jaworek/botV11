package com.tradingbot.persistence.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration for both backtest and production systems.
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.tradingbot.persistence.repositories")
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Value("${mongodb.connection-string:mongodb://admin:password@localhost:27017/}")
    private String connectionString;

    @Value("${mongodb.database:botV11}")
    private String database;

    @Override
    protected String getDatabaseName() {
        return database;
    }

    @Override
    public MongoClient mongoClient() {
        ConnectionString connString = new ConnectionString(connectionString);

        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(connString)
            .build();

        return MongoClients.create(settings);
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), getDatabaseName());
    }
}
