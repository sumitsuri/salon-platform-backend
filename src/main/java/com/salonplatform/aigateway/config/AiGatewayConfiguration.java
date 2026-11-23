package com.salonplatform.aigateway.config;

import com.salonplatform.aigateway.AiGateway;
import com.salonplatform.aigateway.ScopeClassifier;
import com.salonplatform.aigateway.ledger.GatewayLedger;
import com.salonplatform.aigateway.ledger.InMemoryGatewayLedger;
import com.salonplatform.aigateway.policy.CostCalculator;
import com.salonplatform.aigateway.policy.Profile;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wires the gateway from beans an application provides: any number of {@link Profile}s, optionally a durable
 * {@link GatewayLedger} and a {@link ScopeClassifier}. With no ledger it uses an in-memory one.
 */
@Configuration
@EnableConfigurationProperties(AiGatewayProperties.class)
public class AiGatewayConfiguration {

    /** List prices, per million tokens in US dollars. Verify against each provider's price page; override in config. */
    static Map<String, CostCalculator.Price> builtInPrices() {
        Map<String, CostCalculator.Price> m = new LinkedHashMap<>();
        m.put("claude-haiku-5-5", new CostCalculator.Price(0.10, 0.50));
        m.put("claude-sonnet-5-5", new CostCalculator.Price(2.0, 10.0));
        m.put("claude-sonnet-5", new CostCalculator.Price(2.0, 10.0));
        m.put("claude-opus-5-5", new CostCalculator.Price(4.0, 20.0));
        m.put("claude-opus-5", new CostCalculator.Price(5.0, 25.0));
        m.put("claude-fable-5-2", new CostCalculator.Price(8.0, 40.0));
        m.put("gpt-6-luna", new CostCalculator.Price(0.10, 0.50));
        m.put("gpt-6.2-sol", new CostCalculator.Price(2.0, 10.0));
        m.put("grok-4.3", new CostCalculator.Price(1.25, 2.50));
        return m;
    }

    /** Prices for the models we know; anything in configuration overrides or adds to it. */
    @Bean
    public CostCalculator aiGatewayCostCalculator(AiGatewayProperties props) {
        Map<String, CostCalculator.Price> prices = builtInPrices();
        props.getPricing().forEach((k, v) -> prices.put(k, new CostCalculator.Price(v.getInput(), v.getOutput())));
        AiGatewayProperties.Price f = props.getFallbackPrice();
        return new CostCalculator(prices, new CostCalculator.Price(f.getInput(), f.getOutput()), props.getUsdToInr());
    }

    /**
     * The ledger, classifier and clock are optional: an application that supplies none gets an in-memory ledger, no
     * classifier and the system clock. They are looked up here, not declared as conditional beans, because conditions
     * on missing beans are only reliable in auto-configuration.
     */
    @Bean
    public AiGateway aiGateway(ObjectProvider<Profile> profiles, ObjectProvider<GatewayLedger> ledger, CostCalculator aiGatewayCostCalculator,
                               ObjectProvider<ScopeClassifier> classifier, ObjectProvider<Clock> clock, AiGatewayProperties props) {
        List<Profile> all = profiles.orderedStream().toList();
        return new AiGateway(all, ledger.getIfAvailable(InMemoryGatewayLedger::new), aiGatewayCostCalculator,
                classifier.getIfAvailable(), clock.getIfAvailable(Clock::systemUTC), ZoneId.of(props.getTimezone()));
    }
}
