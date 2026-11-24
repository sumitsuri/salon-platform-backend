package com.salonplatform.speech;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Local development and tests only: ignores the audio and "hears" a fixed question, so the whole path can be exercised. */
@Component
@ConditionalOnExpression("${app.speech.enabled:false} and '${app.speech.provider:none}' == 'fake'")
public class FakeSpeechToText implements SpeechToText {
    @Override public String id() { return "fake"; }
    @Override public String model() { return "fake"; }
    @Override public BigDecimal costInrPerMinute() { return new BigDecimal("0.50"); }

    @Override
    public String transcribe(byte[] audio, String mimeType, String language) {
        return "pichle mahine ka sales kitna tha";
    }
}
