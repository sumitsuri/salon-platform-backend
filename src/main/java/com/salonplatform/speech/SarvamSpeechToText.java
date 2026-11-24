package com.salonplatform.speech;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Sarvam AI (built for Indian languages, handles Hindi and English mixed in one sentence). Off unless
 * {@code app.speech.enabled=true}, {@code app.speech.provider=sarvam} and a key are all set.
 */
@Component
@ConditionalOnExpression("${app.speech.enabled:false} and '${app.speech.provider:none}' == 'sarvam' and '${app.speech.sarvam-api-key:}' != ''")
public class SarvamSpeechToText implements SpeechToText {

    private final SpeechProperties props;

    public SarvamSpeechToText(SpeechProperties props) {
        this.props = props;
    }

    @Override public String id() { return "sarvam"; }
    @Override public String model() { return props.getSarvamModel(); }
    @Override public BigDecimal costInrPerMinute() { return props.getSarvamInrPerMinute(); }

    @Override
    public String transcribe(byte[] audio, String mimeType, String language) throws SpeechException {
        // English and Hinglish share one choice in the app; let the engine decide which it heard.
        String code = "en-IN".equals(language) ? "unknown" : language;
        Multipart body = new Multipart()
                .field("model", props.getSarvamModel())
                .field("language_code", code)
                .file("file", Multipart.filenameFor(mimeType), mimeType, audio);
        return HttpSpeech.post(props.getSarvamUrl(), body, props.getTimeoutSeconds(), "api-subscription-key",
                props.getSarvamApiKey(), "transcript");
    }
}
