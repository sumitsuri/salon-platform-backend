package com.salonplatform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Settings for the festive-offer poster studio ({@code app.offer-posters.*}). */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.offer-posters")
public class OfferPosterProperties {

    /** auto (OpenAI when a key is set, else the local preview if allowed) | openai | preview | none. */
    private String provider = "auto";

    /**
     * Allow the stand-in "preview" renderer (a plain drawn poster, not AI art) when no image API key is set.
     * Meant for local development; keep false in production so owners never mistake it for a real poster.
     */
    private boolean allowPreview = false;

    /** Posters one brand may generate per day (each one costs money with a paid image API). */
    private int dailyLimitPerTenant = 15;

    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-image-1";
    /** low | medium | high | auto — drives both quality and cost. */
    private String quality = "medium";
    private int requestTimeoutSeconds = 180;

    private String s3Bucket = "";
    /** Under {@code attendance/} so the prod EC2 IAM role (attendance/*) can write posters. */
    private String keyPrefix = "attendance/offer-posters/";
    private String storageDir = "data/offer-posters";
    private String awsRegion = "ap-south-1";
}
