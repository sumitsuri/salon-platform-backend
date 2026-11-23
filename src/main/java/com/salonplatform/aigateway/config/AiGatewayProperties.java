package com.salonplatform.aigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** Settings the gateway owns, independent of any one application's chat. */
@ConfigurationProperties(prefix = "ai-gateway")
public class AiGatewayProperties {

    /** Time zone that defines "today" and "this month" for limits. */
    private String timezone = "Asia/Kolkata";
    /** Used to turn dollar prices into rupees. */
    private double usdToInr = 88.0;
    /** Per million tokens, in US dollars, keyed by a model name or any part of its id. Empty uses the built-in list. */
    private Map<String, Price> pricing = new LinkedHashMap<>();
    /** Used for a model with no listed price: deliberately a mid-priced model, so unknown models are not free. */
    private Price fallbackPrice = new Price(2.0, 10.0);

    public static class Price {
        private double input;
        private double output;

        public Price() {}

        public Price(double input, double output) {
            this.input = input;
            this.output = output;
        }

        public double getInput() { return input; }
        public void setInput(double input) { this.input = input; }
        public double getOutput() { return output; }
        public void setOutput(double output) { this.output = output; }
    }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public double getUsdToInr() { return usdToInr; }
    public void setUsdToInr(double usdToInr) { this.usdToInr = usdToInr; }
    public Map<String, Price> getPricing() { return pricing; }
    public void setPricing(Map<String, Price> pricing) { this.pricing = pricing; }
    public Price getFallbackPrice() { return fallbackPrice; }
    public void setFallbackPrice(Price fallbackPrice) { this.fallbackPrice = fallbackPrice; }
}
