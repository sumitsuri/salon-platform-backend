package com.salonplatform.demo;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Platform-only controls for the demo showcase brand. */
@RestController
@RequestMapping("/api/v1/platform/demo-data")
@RequiredArgsConstructor
public class DemoDataController {

    private final DemoDataJobService jobService;
    private final DemoTopUpService topUpService;

    /** Wipes and regenerates the demo brand in the background; poll {@code GET /status}. */
    @PostMapping("/rebuild")
    public ApiResponse<DemoDataJobService.Status> rebuild() {
        SecurityUtils.assertPlatformAdmin();
        return ApiResponse.ok(jobService.startRebuild());
    }

    @GetMapping("/status")
    public ApiResponse<DemoDataJobService.Status> status() {
        SecurityUtils.assertPlatformAdmin();
        return ApiResponse.ok(jobService.status());
    }

    /** Runs the "keep today alive" top-up once, synchronously. */
    @PostMapping("/top-up")
    public ApiResponse<Integer> topUp() {
        SecurityUtils.assertPlatformAdmin();
        jobService.assertEnabled();
        return ApiResponse.ok(topUpService.topUp());
    }
}
