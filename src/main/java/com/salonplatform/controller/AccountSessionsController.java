package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.auth.MessageResponse;
import com.salonplatform.dto.auth.SessionResponse;
import com.salonplatform.security.JwtTokenProvider;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** The signed-in user's own devices. Every role can use it; it only ever touches the caller's sessions. */
@RestController
@RequestMapping("/api/v1/account/sessions")
@RequiredArgsConstructor
public class AccountSessionsController {

    private final SessionService sessionService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public ApiResponse<List<SessionResponse>> list(HttpServletRequest request) {
        return ApiResponse.ok(sessionService.list(SecurityUtils.currentUser().getId(), currentSessionId(request)));
    }

    /** Signs out one device (including, if asked, the current one). */
    @DeleteMapping("/{sessionId}")
    public ApiResponse<MessageResponse> revoke(@PathVariable UUID sessionId) {
        sessionService.revoke(SecurityUtils.currentUser().getId(), sessionId);
        return ApiResponse.ok(new MessageResponse("Device signed out."));
    }

    /** Signs out every device except this one. */
    @PostMapping("/revoke-others")
    public ApiResponse<MessageResponse> revokeOthers(HttpServletRequest request) {
        int count = sessionService.revokeOthers(SecurityUtils.currentUser().getId(), currentSessionId(request));
        return ApiResponse.ok(new MessageResponse(count == 1 ? "1 device signed out." : count + " devices signed out."));
    }

    private UUID currentSessionId(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return jwtTokenProvider.getSessionId(bearer.substring(7));
        }
        return null;
    }
}
