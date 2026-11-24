package com.salonplatform.speech;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The two engines are switched off in production; these check what they would send and how they read the reply. */
class SpeechProvidersTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "{}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            auth.set(String.valueOf(ex.getRequestHeaders().getFirst("api-subscription-key")) + "|" + ex.getRequestHeaders().getFirst("Authorization"));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private SpeechProperties props() {
        SpeechProperties p = new SpeechProperties();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        p.setSarvamUrl(url);
        p.setSarvamApiKey("sarvam-key");
        p.setOpenaiUrl(url);
        p.setOpenaiApiKey("openai-key");
        return p;
    }

    @Test
    void sarvamSendsKeyModelAndLanguageAndReadsTranscript() throws Exception {
        reply = "{\"transcript\":\" पिछले महीने की बिक्री कितनी थी \",\"language_code\":\"hi-IN\"}";
        String text = new SarvamSpeechToText(props()).transcribe(new byte[]{1, 2, 3}, "audio/webm;codecs=opus", "hi-IN");
        assertThat(text).isEqualTo("पिछले महीने की बिक्री कितनी थी");
        assertThat(auth.get()).startsWith("sarvam-key|");
        assertThat(body.get()).contains("name=\"model\"").contains("saarika").contains("name=\"language_code\"").contains("hi-IN")
                .contains("filename=\"speech.webm\"");
    }

    @Test
    void sarvamLetsTheEngineDetectEnglishOrHinglish() throws Exception {
        reply = "{\"transcript\":\"last month sales\"}";
        new SarvamSpeechToText(props()).transcribe(new byte[]{1}, "audio/webm", "en-IN");
        assertThat(body.get()).contains("unknown");
    }

    @Test
    void openAiSendsBearerKeyAndLanguageHintExceptForEnglish() throws Exception {
        reply = "{\"text\":\"pichle mahine ka sales\"}";
        SpeechProperties p = props();
        assertThat(new OpenAiSpeechToText(p).transcribe(new byte[]{1}, "audio/mp4", "mr-IN")).isEqualTo("pichle mahine ka sales");
        assertThat(auth.get()).endsWith("|Bearer openai-key");
        assertThat(body.get()).contains("name=\"language\"").contains("\r\n\r\nmr\r\n").contains("filename=\"speech.m4a\"");

        new OpenAiSpeechToText(p).transcribe(new byte[]{1}, "audio/webm", "en-IN");
        assertThat(body.get()).doesNotContain("name=\"language\"");
    }

    @Test
    void aFailingEngineGivesASafeMessageWithoutTheResponseBody() {
        status = 401;
        reply = "{\"error\":\"bad key sarvam-key\"}";
        assertThatThrownBy(() -> new SarvamSpeechToText(props()).transcribe(new byte[]{1}, "audio/webm", "hi-IN"))
                .isInstanceOf(SpeechToText.SpeechException.class)
                .hasMessageNotContaining("sarvam-key").hasMessageNotContaining("401");
    }

    @Test
    void costIsPerMinuteAndSetInSettings() {
        SpeechProperties p = props();
        assertThat(new SarvamSpeechToText(p).costInrPerMinute()).isEqualByComparingTo("0.50");
        assertThat(new OpenAiSpeechToText(p).costInrPerMinute()).isEqualByComparingTo("0.90");
    }
}
