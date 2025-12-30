package com.tradingbot.backtest.reporting;

import com.tradingbot.core.metrics.FilledOrder;
import com.tradingbot.core.metrics.SimulationResult;
import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.ClosedPosition;
import com.tradingbot.core.models.Portfolio;
import com.tradingbot.core.models.Position;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Console reporter for simulation events.
 * Displays detailed information about each action.
 */
public class ConsoleReporter implements SimulationEventListener {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault());

    private final boolean verbose;

    public ConsoleReporter(boolean verbose) {
        this.verbose = verbose;
    }

    public ConsoleReporter() {
        this(true);
    }

    @Override
    public void onSimulationStarted(String algorithmName, String tradingPair, BigDecimal initialBalance) {
        System.out.println("=".repeat(80));
        System.out.println("SIMULATION STARTED");
        System.out.println("=".repeat(80));
        System.out.printf("Algorithm: %s%n", algorithmName);
        System.out.printf("Trading Pair: %s%n", tradingPair);
        System.out.printf("Initial Balance: %.2f%n", initialBalance);
        System.out.println("=".repeat(80));
        System.out.println();
    }

    @Override
    public void onPositionOpened(Position position, Candle candle, Portfolio portfolio) {
        if (!verbose) return;

        String timestamp = DATE_FORMATTER.format(Instant.ofEpochMilli(candle.timestamp()));

        System.out.println("-".repeat(80));
        System.out.printf("[%s] POSITION OPENED%n", timestamp);
        System.out.println("-".repeat(80));
        System.out.printf("Position ID: %s%n", position.getId().substring(0, 8));
        System.out.printf("Side: %s%n", position.getSide());
        System.out.printf("Entry Price: %.2f%n", position.getEntryPrice());
        System.out.printf("Quantity: %.4f%n", position.getQuantity());
        System.out.printf("Cost: %.2f%n", position.getQuantity().multiply(position.getEntryPrice()));

        if (position.getMetadata() != null) {
            System.out.printf("Metadata: %s%n", position.getMetadata());
        }

        System.out.println();
        printPortfolioState(portfolio, candle.close());
        System.out.println();
    }

    @Override
    public void onPositionClosed(ClosedPosition position, Candle candle, Portfolio portfolio) {
        if (!verbose) return;

        String timestamp = DATE_FORMATTER.format(Instant.ofEpochMilli(candle.timestamp()));

        System.out.println("-".repeat(80));
        System.out.printf("[%s] POSITION CLOSED%n", timestamp);
        System.out.println("-".repeat(80));
        System.out.printf("Position ID: %s%n", position.getId().substring(0, 8));
        System.out.printf("Side: %s%n", position.getSide());
        System.out.printf("Entry Price: %.2f%n", position.getEntryPrice());
        System.out.printf("Exit Price: %.2f%n", position.getExitPrice());
        System.out.printf("Quantity: %.4f%n", position.getQuantity());
        System.out.printf("Realized PnL: %.2f (%.2f%%)%n",
            position.getRealizedPnL(),
            position.getRealizedPnLPercentage());

        if (position.getMetadata() != null) {
            System.out.printf("Metadata: %s%n", position.getMetadata());
        }

        long durationSeconds = (position.getCloseTimestamp() - position.getOpenTimestamp()) / 1000;
        System.out.printf("Duration: %d seconds (%.2f minutes)%n",
            durationSeconds, durationSeconds / 60.0);

        System.out.println();
        printPortfolioState(portfolio, candle.close());
        System.out.println();
    }

    @Override
    public void onSimulationInterrupted(String reason) {
        System.out.println("!".repeat(80));
        System.out.println("SIMULATION INTERRUPTED");
        System.out.println("!".repeat(80));
        System.out.printf("Reason: %s%n", reason);
        System.out.println("!".repeat(80));
        System.out.println();
    }

    @Override
    public void onSimulationCompleted(SimulationResult result) {
        System.out.println("=".repeat(80));
        System.out.println("SIMULATION COMPLETED");
        System.out.println("=".repeat(80));
        System.out.println();

        System.out.println("FINAL RESULTS:");
        System.out.println("-".repeat(80));

        System.out.printf("Algorithm: %s%n", result.getAlgorithmName());
        System.out.printf("Trading Pair: %s%n", result.getTradingPair());
        System.out.printf("Duration: %d ms (%.2f hours)%n",
            result.getEndTimestamp() - result.getStartTimestamp(),
            (result.getEndTimestamp() - result.getStartTimestamp()) / 3600000.0);

        if (result.isInterrupted()) {
            System.out.printf("Status: INTERRUPTED - %s%n", result.getInterruptionReason());
        } else {
            System.out.println("Status: COMPLETED");
        }

        System.out.println();
        System.out.println("PROFIT METRICS:");
        System.out.println("-".repeat(80));
        System.out.printf("Initial Balance: %.2f%n", result.getInitialBalance());
        System.out.printf("Final Equity: %.2f%n", result.getFinalEquity());
        System.out.printf("Profit (Absolute): %.2f%n", result.getProfitAbsolute());
        System.out.printf("Profit (Percentage): %.2f%%%n", result.getProfitPercentage());

        System.out.println();
        System.out.println("TRADING ACTIVITY:");
        System.out.println("-".repeat(80));
        System.out.printf("Total Trades Executed: %d%n", result.getTotalTradesExecuted());
        System.out.printf("Open Positions at End: %d%n", result.getOpenPositionsAtEnd());

        System.out.println();
        System.out.println("EQUITY METRICS:");
        System.out.println("-".repeat(80));
        System.out.printf("Max Equity (with positions): %.2f%n", result.getMaxEquityWithPositions());
        System.out.printf("Min Equity (with positions): %.2f%n", result.getMinEquityWithPositions());
        System.out.printf("Max Cash Balance (without positions): %.2f%n", result.getMaxCashBalance());
        System.out.printf("Min Cash Balance (without positions): %.2f%n", result.getMinCashBalance());

        System.out.println();
        System.out.println("DRAWDOWN METRICS:");
        System.out.println("-".repeat(80));
        System.out.printf("Max Position Drawdown: %.2f%%%n", result.getMaxPositionDrawdownPercentage());
        System.out.printf("Max Portfolio Drawdown: %.2f%%%n", result.getMaxPortfolioDrawdownPercentage());

        System.out.println("=".repeat(80));
        System.out.println();

        // Display filled orders table
        printFilledOrdersTable(result.getFilledOrders());
    }

    /**
     * Display filled orders in a table format, sorted by date
     */
    public static void printFilledOrdersTable(List<FilledOrder> filledOrders) {
        if (filledOrders == null || filledOrders.isEmpty()) {
            System.out.println("No filled orders to display.");
            System.out.println();
            return;
        }

        System.out.println("=".repeat(120));
        System.out.println("FILLED ORDERS (CHRONOLOGICAL)");
        System.out.println("=".repeat(120));
        System.out.println();

        // Header
        System.out.printf("%-20s | %-6s | %-10s | %-12s | %-12s | %-12s | %-12s%n",
            "Date/Time", "Type", "Pos ID", "Avg Entry", "Close Price", "Volume", "P&L");
        System.out.println("-".repeat(120));

        // Data rows
        for (FilledOrder order : filledOrders) {
            String timestamp = DATE_FORMATTER.format(Instant.ofEpochMilli(order.getTimestamp()));
            String type = order.getType().toString();
            String posId = order.getPositionId().substring(0, 8);
            String avgEntry = String.format("%.2f", order.getAvgEntry());
            String closePrice = order.getType() == FilledOrder.OrderType.CLOSE
                ? String.format("%.2f", order.getPrice())
                : "-";
            String volume = String.format("%.8f", order.getQuantity());
            String pnl = order.getRealizedPnL() != null
                ? String.format("%.2f", order.getRealizedPnL())
                : "-";

            System.out.printf("%-20s | %-6s | %-10s | %-12s | %-12s | %-12s | %-12s%n",
                timestamp, type, posId, avgEntry, closePrice, volume, pnl);
        }

        System.out.println("=".repeat(120));
        System.out.println();
    }

    private void printPortfolioState(Portfolio portfolio, BigDecimal currentPrice) {
        System.out.println("Portfolio State:");
        System.out.printf("  Cash Balance: %.2f%n", portfolio.getCashBalance());
        System.out.printf("  Open Positions: %d%n", portfolio.getOpenPositionCount());
        System.out.printf("  Closed Positions: %d%n", portfolio.getClosedPositionCount());
        System.out.printf("  Current Equity: %.2f%n", portfolio.getEquity(currentPrice));
        System.out.printf("  Total Realized PnL: %.2f%n", portfolio.getTotalRealizedPnL());
        System.out.printf("  Total Unrealized PnL: %.2f%n", portfolio.getTotalUnrealizedPnL(currentPrice));
    }
}
