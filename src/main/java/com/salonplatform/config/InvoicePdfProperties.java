package com.salonplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.invoice-pdf")
public class InvoicePdfProperties {
    /**
     * When set, invoice PDFs are uploaded to this S3 bucket under {@link #keyPrefix}.
     * When empty, PDFs are stored under {@link #localDir} (dev / fallback).
     */
    private String s3Bucket = "";
    private String keyPrefix = "invoices/";
    private String localDir = "data/invoice-pdfs";
    private String awsRegion = "ap-south-1";
}
