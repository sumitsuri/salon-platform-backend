package com.salonplatform.offerposter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.config.OfferPosterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiPosterImageProviderTest {

    private MockRestServiceServer server;
    private OpenAiPosterImageProvider provider;
    private final OfferContent content = OfferCopyTest.content(OfferCopyTest.spend(OfferCopyTest.tier("999", "10", null)));

    @BeforeEach
    void setUp() {
        OfferPosterProperties props = new OfferPosterProperties();
        props.setApiKey("sk-test");
        props.setBaseUrl("https://api.example.test/v1");
        RestTemplate rt = new RestTemplate();
        server = MockRestServiceServer.createServer(rt);
        provider = new OpenAiPosterImageProvider(props, new ObjectMapper(), rt);
    }

    @Test
    void sendsTheModelPromptSizeAndKeyAndDecodesTheImage() {
        byte[] jpeg = {1, 2, 3, 4};
        server.expect(requestTo("https://api.example.test/v1/images/generations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("gpt-image-1"))
                .andExpect(jsonPath("$.size").value("1024x1536"))
                .andExpect(jsonPath("$.quality").value("medium"))
                .andExpect(jsonPath("$.output_format").value("jpeg"))
                .andExpect(jsonPath("$.prompt").value("PROMPT"))
                .andRespond(withSuccess("{\"data\":[{\"b64_json\":\"" + Base64.getEncoder().encodeToString(jpeg) + "\"}]}",
                        MediaType.APPLICATION_JSON));

        PosterImageProvider.GeneratedImage img = provider.generate("PROMPT", content);

        assertArrayEquals(jpeg, img.bytes());
        assertEquals("image/jpeg", img.contentType());
        server.verify();
    }

    private String failure(HttpStatus status, String body) {
        server.expect(requestTo("https://api.example.test/v1/images/generations"))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
        return assertThrows(PosterGenerationException.class, () -> provider.generate("p", content)).getMessage();
    }

    @Test
    void failuresBecomeFriendlyMessagesAndNeverLeakTheKeyOrBody() {
        assertTrue(failure(HttpStatus.UNAUTHORIZED, "{\"error\":{\"message\":\"Incorrect API key sk-test\"}}").contains("API key"));
    }

    @Test
    void organisationVerificationIsExplained() {
        assertTrue(failure(HttpStatus.FORBIDDEN, "{\"error\":{\"message\":\"Your organization must be verified to use gpt-image-1\"}}")
                .contains("verification"));
    }

    @Test
    void outOfCreditIsDistinguishedFromRateLimiting() {
        assertTrue(failure(HttpStatus.TOO_MANY_REQUESTS, "{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"You exceeded your current quota, check billing\"}}")
                .contains("credit"));
    }

    @Test
    void rateLimitingAsksToRetry() {
        assertTrue(failure(HttpStatus.TOO_MANY_REQUESTS, "{\"error\":{\"message\":\"Rate limit reached\"}}").contains("busy"));
    }

    @Test
    void safetyRefusalSuggestsDifferentWording() {
        assertTrue(failure(HttpStatus.BAD_REQUEST, "{\"error\":{\"code\":\"moderation_blocked\",\"message\":\"Your request was rejected by the safety system.\"}}")
                .contains("wording"));
    }

    @Test
    void serverErrorsAskToRetry() {
        assertTrue(failure(HttpStatus.BAD_GATEWAY, "oops").contains("try again"));
    }

    @Test
    void emptyOrUnreadableResponsesAreHandled() {
        server.expect(requestTo("https://api.example.test/v1/images/generations"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        assertThrows(PosterGenerationException.class, () -> provider.generate("p", content));
        server.reset();
        server.expect(requestTo("https://api.example.test/v1/images/generations"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        assertThrows(PosterGenerationException.class, () -> provider.generate("p", content));
    }
}
