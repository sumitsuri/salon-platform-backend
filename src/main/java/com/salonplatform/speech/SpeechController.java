package com.salonplatform.speech;

import com.salonplatform.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-side speech to text for the assistant's microphone. Answers 503 until an engine is switched on. */
@RestController
@RequestMapping("/api/v1/copilot")
@RequiredArgsConstructor
public class SpeechController {

    private final SpeechService service;

    @PostMapping("/transcribe")
    public ApiResponse<Map<String, Object>> transcribe(@RequestParam("audio") MultipartFile audio,
                                                       @RequestParam("lang") String lang,
                                                       @RequestParam(value = "seconds", defaultValue = "10") int seconds) throws IOException {
        SpeechService.Result r = service.transcribe(audio.getBytes(), audio.getContentType(), lang, seconds);
        return ApiResponse.ok(Map.of("text", r.text(), "engine", r.engine()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", e.getStatusCode().value());
        body.put("code", "error.speech");
        body.put("message", e.getReason());
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }
}
