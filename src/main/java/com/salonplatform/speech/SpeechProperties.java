package com.salonplatform.speech;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Settings for server-side speech to text. Everything is off by default; the browser's own recogniser keeps working. */
@Data
@Component
@ConfigurationProperties(prefix = "app.speech")
public class SpeechProperties {
    /** Master switch. While false no audio endpoint answers and no provider bean exists, whatever the keys say. */
    private boolean enabled = false;
    /** {@code none}, {@code sarvam}, {@code openai}, or {@code fake} (returns a fixed sentence; local development and tests). */
    private String provider = "none";
    /** Longest recording accepted, in seconds. Spoken questions are 5 to 15 seconds. */
    private int maxSeconds = 30;
    /** Largest upload accepted, in bytes. */
    private int maxBytes = 1_000_000;
    /** Audio one person may send per day, in seconds. */
    private int dailySecondsPerUser = 900;
    /** Give up on the engine after this long. */
    private int timeoutSeconds = 20;

    private String sarvamApiKey = "";
    private String sarvamUrl = "https://api.sarvam.ai/speech-to-text";
    private String sarvamModel = "saarika:v2.5";
    /** List price is about 0.49 rupees a minute; counted against the business's spend limit. */
    private BigDecimal sarvamInrPerMinute = new BigDecimal("0.50");

    private String openaiApiKey = "";
    private String openaiUrl = "https://api.openai.com/v1/audio/transcriptions";
    private String openaiModel = "gpt-4o-mini-transcribe";
    /** List price is about $0.003 a minute (about 0.27 rupees); real bills have been reported higher, so budget on 0.9. */
    private BigDecimal openaiInrPerMinute = new BigDecimal("0.90");
}
