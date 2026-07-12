package com.roleplay.engine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Tracks LLM usage, costs, and budget enforcement.
 * Maps from Python core/monitor.py → Monitor.
 */
@Service
public class Monitor {
    private static final Logger log = LoggerFactory.getLogger(Monitor.class);

    private static final Map<String, double[]> MODEL_COST_PER_1M = new HashMap<>();
    static {
        MODEL_COST_PER_1M.put("deepseek/deepseek-v4-flash", new double[]{0.15, 0.60});
        MODEL_COST_PER_1M.put("gpt-4o-mini", new double[]{0.15, 0.60});
        MODEL_COST_PER_1M.put("gpt-4o", new double[]{2.50, 10.00});
        MODEL_COST_PER_1M.put("claude-3-haiku", new double[]{0.25, 1.25});
        MODEL_COST_PER_1M.put("claude-3-sonnet", new double[]{3.00, 15.00});
    }

    private double budget;
    private final List<UsageRecord> records = Collections.synchronizedList(new ArrayList<>());
    private final Deque<Double> recentCosts = new ConcurrentLinkedDeque<>();
    private static final int RECENT_WINDOW = 50;

    public Monitor() { this.budget = 10.0; }

    public void setBudget(double budget) { this.budget = budget; }
    public double getBudget() { return budget; }

    /** Record an LLM usage event. Returns cost in USD. */
    public double recordUsage(String model, int promptTokens, int completionTokens,
                              boolean success, String error) {
        UsageRecord rec = new UsageRecord(model, promptTokens, completionTokens, success, error);
        double cost = rec.calculateCost();
        records.add(rec);
        synchronized (recentCosts) {
            recentCosts.addLast(cost);
            if (recentCosts.size() > RECENT_WINDOW) recentCosts.removeFirst();
        }
        return cost;
    }

    public double recordUsage(String model, int promptTokens, int completionTokens) {
        return recordUsage(model, promptTokens, completionTokens, true, null);
    }

    /** Generate comprehensive cost report. */
    public Map<String, Object> getCostReport() {
        int total = records.size();
        int success = (int) records.stream().filter(r -> r.success).count();
        double totalCost = records.stream().mapToDouble(UsageRecord::calculateCost).sum();
        double avgCost = total > 0 ? totalCost / total : 0;
        double recentAvg = recentCosts.isEmpty() ? 0 :
            recentCosts.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double remaining = Math.max(0, budget - totalCost);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total_tokens", records.stream().mapToInt(UsageRecord::totalTokens).sum());
        report.put("total_cost_usd", Math.round(totalCost * 1_000_000.0) / 1_000_000.0);
        report.put("budget_usd", budget);
        report.put("estimated_remaining_usd", Math.round(remaining * 1_000_000.0) / 1_000_000.0);
        report.put("budget_used_pct", budget > 0 ?
            Math.round(totalCost / budget * 10000.0) / 100.0 : 0);
        report.put("total_calls", total);
        report.put("successful_calls", success);
        report.put("failed_calls", total - success);
        report.put("avg_cost_per_call_usd", Math.round(avgCost * 1_000_000.0) / 1_000_000.0);
        return report;
    }

    public boolean isBudgetExhausted() {
        double totalCost = records.stream().mapToDouble(UsageRecord::calculateCost).sum();
        return totalCost >= budget;
    }

    public double getTotalCost() {
        return records.stream().mapToDouble(UsageRecord::calculateCost).sum();
    }

    public int getTotalTokens() {
        return records.stream().mapToInt(UsageRecord::totalTokens).sum();
    }

    /** Usage record inner class. */
    public static class UsageRecord {
        public final String model;
        public final int promptTokens;
        public final int completionTokens;
        public final Instant timestamp;
        public final boolean success;
        public final String error;

        public UsageRecord(String model, int promptTokens, int completionTokens,
                          boolean success, String error) {
            this.model = model;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.timestamp = Instant.now();
            this.success = success;
            this.error = error;
        }

        public int totalTokens() { return promptTokens + completionTokens; }

        public double calculateCost() {
            double[] rates = MODEL_COST_PER_1M.getOrDefault(model, new double[]{0.15, 0.60});
            return (promptTokens / 1_000_000.0) * rates[0]
                 + (completionTokens / 1_000_000.0) * rates[1];
        }
    }
}
