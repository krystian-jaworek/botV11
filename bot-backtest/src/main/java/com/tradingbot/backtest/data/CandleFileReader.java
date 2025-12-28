package com.tradingbot.backtest.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingbot.core.models.Candle;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads candle data from text files.
 * File format: <PAIR>-<INTERVAL>-<DAYS>.txt
 * Each line is a JSON candle: {"open": 88128.40, "close": 88119.80, "high": 88128.50, "low": 88116.50, "timestamp": "1766250900000"}
 */
@Slf4j
public class CandleFileReader {

    private final ObjectMapper objectMapper;

    public CandleFileReader() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Read all candles from a file.
     *
     * @param filePath Path to the candle file
     * @return List of candles in chronological order
     * @throws IOException if file cannot be read
     */
    public List<Candle> readCandles(Path filePath) throws IOException {
        log.info("Reading candles from file: {}", filePath);

        List<Candle> candles = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                try {
                    Candle candle = objectMapper.readValue(line, Candle.class);
                    candles.add(candle);
                } catch (Exception e) {
                    log.error("Failed to parse candle at line {}: {}", lineNumber, line, e);
                    throw new IOException("Failed to parse candle at line " + lineNumber, e);
                }
            }
        }

        log.info("Loaded {} candles from {}", candles.size(), filePath.getFileName());

        if (candles.isEmpty()) {
            throw new IOException("No candles found in file: " + filePath);
        }

        return candles;
    }

    /**
     * Read candles from a file in the specified directory.
     *
     * @param dataDirectory Directory containing candle files
     * @param pair Trading pair (e.g., "BTCUSDT")
     * @param interval Interval in minutes (e.g., 1, 5, 15)
     * @param days Number of days of data
     * @return List of candles
     * @throws IOException if file cannot be read
     */
    public List<Candle> readCandles(Path dataDirectory, String pair, int interval, int days) throws IOException {
        String fileName = String.format("%s-%d-%d.txt", pair, interval, days);
        Path filePath = dataDirectory.resolve(fileName);

        if (!Files.exists(filePath)) {
            throw new IOException("Candle file not found: " + filePath);
        }

        return readCandles(filePath);
    }

    /**
     * Read candles from classpath resources.
     * File should be in src/main/resources/ or src/test/resources/
     *
     * @param fileName Name of the file in resources (e.g., "BTCUSDT-1-365.txt")
     * @return List of candles in chronological order
     * @throws IOException if file cannot be read
     */
    public List<Candle> readCandlesFromClasspath(String fileName) throws IOException {
        log.info("Reading candles from classpath: {}", fileName);

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        if (inputStream == null) {
            throw new IOException("Candle file not found in classpath: " + fileName);
        }

        return readCandlesFromStream(inputStream, fileName);
    }

    /**
     * Read candles from an input stream
     */
    private List<Candle> readCandlesFromStream(InputStream inputStream, String sourceName) throws IOException {
        List<Candle> candles = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                try {
                    Candle candle = objectMapper.readValue(line, Candle.class);
                    candles.add(candle);
                } catch (Exception e) {
                    log.error("Failed to parse candle at line {}: {}", lineNumber, line, e);
                    throw new IOException("Failed to parse candle at line " + lineNumber, e);
                }
            }
        }

        log.info("Loaded {} candles from {}", candles.size(), sourceName);

        if (candles.isEmpty()) {
            throw new IOException("No candles found in: " + sourceName);
        }

        return candles;
    }

    /**
     * Parse file name to extract metadata.
     * Format: <PAIR>-<INTERVAL>-<DAYS>.txt
     */
    public static CandleFileMetadata parseFileName(String fileName) {
        if (!fileName.endsWith(".txt")) {
            throw new IllegalArgumentException("Invalid file name format: " + fileName);
        }

        String nameWithoutExtension = fileName.substring(0, fileName.length() - 4);
        String[] parts = nameWithoutExtension.split("-");

        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid file name format: " + fileName);
        }

        try {
            return new CandleFileMetadata(
                parts[0],
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid file name format: " + fileName, e);
        }
    }

    /**
     * Metadata extracted from candle file name
     */
    public record CandleFileMetadata(
        String pair,
        int intervalMinutes,
        int days
    ) {}
}
