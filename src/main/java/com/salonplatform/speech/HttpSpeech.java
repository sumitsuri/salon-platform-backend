package com.salonplatform.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** The one HTTP call both engines make: post a multipart body, read one field from the JSON that comes back. */
final class HttpSpeech {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private HttpSpeech() {}

    static String post(String url, Multipart body, int timeoutSeconds, String headerName, String headerValue, String field)
            throws SpeechToText.SpeechException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", body.contentType())
                    .header(headerName, headerValue)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.build()))
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // The body can echo the request, so it is not logged or shown; the status is enough to diagnose.
                throw new SpeechToText.SpeechException("The speech service could not understand that recording.",
                        new IOException("HTTP " + response.statusCode()));
            }
            JsonNode json = MAPPER.readTree(response.body());
            return json.path(field).asText("").trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpeechToText.SpeechException("The speech service did not answer in time.", e);
        } catch (IOException | RuntimeException e) {
            throw new SpeechToText.SpeechException("The speech service is not reachable right now.", e);
        }
    }
}
