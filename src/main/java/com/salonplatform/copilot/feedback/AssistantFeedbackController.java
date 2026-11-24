package com.salonplatform.copilot.feedback;

import com.salonplatform.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AssistantFeedbackController {

    private final AssistantFeedbackService service;

    /** A person rates an answer, or says whether the microphone heard them right. */
    @PostMapping("/api/v1/copilot/feedback")
    public ApiResponse<Map<String, Object>> submit(@RequestBody AssistantFeedbackService.Feedback body) {
        service.submit(body);
        return ApiResponse.ok(Map.of("saved", true));
    }

    @GetMapping("/api/v1/platform/ai-gateway/feedback")
    public ApiResponse<Map<String, Object>> summary(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(service.summary(Math.max(1, Math.min(days, 365))));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", e.getStatusCode().value());
        body.put("code", "error.feedback");
        body.put("message", e.getReason());
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }
}
