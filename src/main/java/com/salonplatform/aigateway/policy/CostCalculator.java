package com.salonplatform.aigateway.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Turns token counts into rupees, so spend can be limited per business. Prices are per million tokens, in US dollars. */
public final class CostCalculator {

    public record Price(double inputPerMillionUsd, double outputPerMillionUsd) {}

    private final Map<String, Price> prices;
    private final Price fallback;
    private final double usdToInr;

    public CostCalculator(Map<String, Price> prices, Price fallback, double usdToInr) {
        this.prices = Map.copyOf(prices);
        this.fallback = fallback;
        this.usdToInr = usdToInr;
    }

    /** The longest configured model name contained in {@code model}, so "global.anthropic.claude-sonnet-5-5" still prices. */
    Price priceOf(String model) {
        if (model == null) return fallback;
        String m = model.toLowerCase();
        Price best = null;
        int bestLen = -1;
        for (Map.Entry<String, Price> e : prices.entrySet()) {
            String key = e.getKey().toLowerCase();
            if (m.contains(key) && key.length() > bestLen) {
                best = e.getValue();
                bestLen = key.length();
            }
        }
        return best != null ? best : fallback;
    }

    public BigDecimal costInr(String model, long inputTokens, long outputTokens) {
        Price p = priceOf(model);
        double usd = inputTokens / 1_000_000.0 * p.inputPerMillionUsd() + outputTokens / 1_000_000.0 * p.outputPerMillionUsd();
        return BigDecimal.valueOf(usd * usdToInr).setScale(4, RoundingMode.HALF_UP);
    }
}
