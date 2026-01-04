package com.tradingbot.production;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application for production trading system.
 */
@SpringBootApplication
@EnableScheduling
@EnableMongoRepositories(basePackages = "com.tradingbot.production.repository")
@ComponentScan(basePackages = {
    "com.tradingbot.production",
    "com.tradingbot.persistence"
})
public class TradingBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingBotApplication.class, args);
    }
}
