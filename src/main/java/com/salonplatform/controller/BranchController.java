package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.branch.BranchResponse;
import com.salonplatform.dto.branch.CreateBranchRequest;
import com.salonplatform.service.BranchManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchManagementService branchService;

    @PostMapping
    public ApiResponse<BranchResponse> create(@Valid @RequestBody CreateBranchRequest request) {
        return ApiResponse.ok(branchService.create(request));
    }

    @GetMapping
    public ApiResponse<List<BranchResponse>> list() {
        return ApiResponse.ok(branchService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<BranchResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(branchService.get(id));
    }
}
