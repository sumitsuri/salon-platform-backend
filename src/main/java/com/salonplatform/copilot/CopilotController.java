package com.salonplatform.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.copilot.llm.LlmMessage;
import com.salonplatform.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owner-facing chat. Streams the answer as server-sent events: text, tool, done, error. */
@RestController
@RequestMapping("/api/v1/copilot")
@RequiredArgsConstructor
@Slf4j
public class CopilotController {

    public record ChatRequest(List<CopilotService.ChatMessage> messages) {}

    private final CopilotService service;
    private final ObjectMapper mapper;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(service.status());
    }

    @PostMapping("/chat")
    public void chat(@RequestBody ChatRequest body, HttpServletResponse response) throws IOException {
        // Access, limits and input are checked first, so they fail as ordinary HTTP errors, not mid-stream.
        List<LlmMessage> conversation = service.prepare(body.messages());

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/event-stream");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no"); // keep nginx from holding the stream back
        SseWriter sse = new SseWriter(response.getOutputStream());
        try {
            service.run(conversation, sse);
        } catch (ClientGoneException e) {
            log.debug("assistant client disconnected");
        }
    }

    /** Limit, not-enabled and bad-input failures happen before streaming starts, so they are ordinary JSON errors. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", e.getStatusCode().value());
        body.put("code", "error.assistant");
        body.put("message", e.getReason());
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    /** Thrown when the browser has gone away, to stop paying for an answer nobody is reading. */
    static final class ClientGoneException extends RuntimeException {
        ClientGoneException(Throwable cause) {
            super(cause);
        }
    }

    private final class SseWriter implements CopilotService.Events {
        private final OutputStream out;

        SseWriter(OutputStream out) {
            this.out = out;
        }

        @Override
        public void text(String delta) {
            send("text", Map.of("t", delta));
        }

        @Override
        public void toolStart(String name, String label) {
            send("tool", Map.of("name", name, "label", label, "phase", "start"));
        }

        @Override
        public void toolEnd(String name, boolean ok, long millis) {
            send("tool", Map.of("name", name, "phase", "end", "ok", ok, "ms", millis));
        }

        @Override
        public void done(int rounds, int inputTokens, int outputTokens) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("rounds", rounds);
            d.put("inputTokens", inputTokens);
            d.put("outputTokens", outputTokens);
            send("done", d);
        }

        @Override
        public void error(String message) {
            try {
                send("error", Map.of("message", message));
            } catch (ClientGoneException ignored) {
                // nobody is listening
            }
        }

        private synchronized void send(String event, Object data) {
            try {
                out.write(("event: " + event + "\ndata: " + mapper.writeValueAsString(data) + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                throw new ClientGoneException(e);
            }
        }
    }
}
