package com.salonplatform.offerposter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.config.OfferPosterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Generates posters with the OpenAI Images API (gpt-image-1 family). */
@Slf4j
public class OpenAiPosterImageProvider implements PosterImageProvider {

    private final OfferPosterProperties props;
    private final ObjectMapper mapper;
    private final RestTemplate http;

    public OpenAiPosterImageProvider(OfferPosterProperties props, ObjectMapper mapper) {
        this(props, mapper, buildClient(props));
    }

    OpenAiPosterImageProvider(OfferPosterProperties props, ObjectMapper mapper, RestTemplate http) {
        this.props = props;
        this.mapper = mapper;
        this.http = http;
    }

    private static RestTemplate buildClient(OfferPosterProperties props) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(Math.max(30, props.getRequestTimeoutSeconds())).toMillis());
        return new RestTemplate(f);
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public String model() {
        return props.getModel();
    }

    @Override
    public GeneratedImage generate(String prompt, OfferContent content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("prompt", prompt);
        body.put("size", content.format().size());
        body.put("quality", props.getQuality());
        body.put("n", 1);
        body.put("output_format", "jpeg");
        body.put("output_compression", 90);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getApiKey());

        String raw;
        try {
            raw = http.postForObject(props.getBaseUrl() + "/images/generations", new HttpEntity<>(body, headers), String.class);
        } catch (HttpStatusCodeException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            log.warn("Poster generation timed out or could not connect: {}", e.getMessage());
            throw new PosterGenerationException(
                    "The image service took too long to respond. Please try again in a minute.", e);
        }

        try {
            JsonNode data = mapper.readTree(raw).path("data").path(0);
            String b64 = data.path("b64_json").asText("");
            if (b64.isEmpty()) {
                throw new PosterGenerationException("The image service returned no image. Please try again.");
            }
            return new GeneratedImage(Base64.getDecoder().decode(b64), "image/jpeg");
        } catch (PosterGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not read image response: {}", e.toString());
            throw new PosterGenerationException("The image service sent an unreadable response. Please try again.", e);
        }
    }

    PosterGenerationException translate(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        log.warn("Image API error {}: {}", e.getStatusCode().value(), truncate(body));
        int status = e.getStatusCode().value();
        String lower = body == null ? "" : body.toLowerCase();
        if (status == 401) {
            return new PosterGenerationException("The image service rejected our API key. Ask your administrator to check it.");
        }
        if (status == 403 || lower.contains("must be verified")) {
            return new PosterGenerationException(
                    "The image service has not approved this account for image generation yet (organisation verification is required).");
        }
        if (status == 429) {
            return new PosterGenerationException(
                    lower.contains("quota") || lower.contains("billing")
                            ? "The image service account is out of credit. Ask your administrator to top it up."
                            : "The image service is busy right now. Please try again in a minute.");
        }
        if (status == 400 && (lower.contains("safety") || lower.contains("moderation") || lower.contains("content_policy"))) {
            return new PosterGenerationException(
                    "The image service declined this design. Try different wording in the headline or extra direction.");
        }
        if (status >= 500) {
            return new PosterGenerationException("The image service had a problem. Please try again shortly.");
        }
        return new PosterGenerationException("The image service could not create this poster (error " + status + ").");
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }
}
