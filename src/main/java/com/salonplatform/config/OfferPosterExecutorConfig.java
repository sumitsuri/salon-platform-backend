package com.salonplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** Image generation takes 20-90 s, so it runs on its own small pool instead of tying up request threads. */
@Configuration
public class OfferPosterExecutorConfig {

    @Bean(name = "offerPosterExecutor")
    public Executor offerPosterExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(30);
        ex.setThreadNamePrefix("offer-poster-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ex.initialize();
        return ex;
    }
}
