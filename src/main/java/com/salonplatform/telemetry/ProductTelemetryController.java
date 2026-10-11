package com.salonplatform.telemetry;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
public class ProductTelemetryController {

    private final ProductTelemetryIngestService ingestService;

    @PostMapping("/events")
    public ResponseEntity<ApiResponse<Void>> ingest(@Valid @RequestBody IngestTelemetryEventRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        ingestService.acceptFromClient(user, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok("Accepted", null));
    }
}
