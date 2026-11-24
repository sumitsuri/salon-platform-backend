package com.salonplatform.speech;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * OpenAI transcription models (gpt-4o-mini-transcribe by default). Off unless {@code app.speech.enabled=true},
 * {@code app.speech.provider=openai} and a key are all set.
 */
@Component
@ConditionalOnExpression("${app.speech.enabled:false} and '${app.speech.provider:none}' == 'openai' and '${app.speech.openai-api-key:}' != ''")
public class OpenAiSpeechToText implements SpeechToText {

    private final SpeechProperties props;

    public OpenAiSpeechToText(SpeechProperties props) {
        this.props = props;
    }

    @Override public String id() { return "openai"; }
    @Override public String model() { return props.getOpenaiModel(); }
    @Override public BigDecimal costInrPerMinute() { return props.getOpenaiInrPerMinute(); }

    @Override
    public String transcribe(byte[] audio, String mimeType, String language) throws SpeechException {
        Multipart body = new Multipart()
                .field("model", props.getOpenaiModel())
                .field("response_format", "json");
        // A language hint helps, but English plus Hinglish is one choice in the app, so leave it open there.
        if (language != null && !language.startsWith("en")) {
            body.field("language", language.substring(0, 2));
        }
        body.file("file", Multipart.filenameFor(mimeType), mimeType, audio);
        return HttpSpeech.post(props.getOpenaiUrl(), body, props.getTimeoutSeconds(), "Authorization",
                "Bearer " + props.getOpenaiApiKey(), "text");
    }
}
