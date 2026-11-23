package com.salonplatform.copilot.gateway;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** AntraHQ's own view of AI usage: which businesses ask how much, what is refused, and what it costs. */
@RestController
@RequestMapping("/api/v1/platform/ai-gateway")
@RequiredArgsConstructor
public class AiGatewayAdminController {

    private final JdbcGatewayLedger ledger;

    @GetMapping("/usage")
    public ApiResponse<List<Map<String, Object>>> usage(@RequestParam(defaultValue = "30") int days) {
        SecurityUtils.assertPlatformAdmin();
        return ApiResponse.ok(ledger.summaryByTenant(Math.max(1, Math.min(days, 365))));
    }
}
