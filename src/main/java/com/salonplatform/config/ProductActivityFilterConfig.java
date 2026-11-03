package com.salonplatform.config;

import com.salonplatform.telemetry.ProductActivityFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductActivityFilterConfig {

    /** The filter runs inside the security chain, after JWT auth. Do not also map it as a servlet filter. */
    @Bean
    public FilterRegistrationBean<ProductActivityFilter> productActivityFilterRegistration(ProductActivityFilter filter) {
        FilterRegistrationBean<ProductActivityFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
