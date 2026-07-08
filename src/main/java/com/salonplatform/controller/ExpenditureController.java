package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.expenditure.CreateExpenditureRequest;
import com.salonplatform.dto.expenditure.ExpenditureResponse;
import com.salonplatform.dto.expenditure.UpdateExpenditureRequest;
import com.salonplatform.service.ExpenditureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenditures")
@RequiredArgsConstructor
public class ExpenditureController {

    private final ExpenditureService expenditureService;

    @GetMapping
    public ApiResponse<List<ExpenditureResponse>> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromMonth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toMonth) {
        return ApiResponse.ok(expenditureService.list(branchId, fromMonth, toMonth));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExpenditureResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(expenditureService.get(id));
    }

    @PostMapping
    public ApiResponse<ExpenditureResponse> create(@Valid @RequestBody CreateExpenditureRequest request) {
        return ApiResponse.ok(expenditureService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExpenditureResponse> update(
            @PathVariable UUID id, @RequestBody UpdateExpenditureRequest request) {
        return ApiResponse.ok(expenditureService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deactivate(@PathVariable UUID id) {
        expenditureService.deactivate(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/sync-payroll")
    public ApiResponse<List<ExpenditureResponse>> syncPayroll(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expenseMonth) {
        return ApiResponse.ok(expenditureService.syncPayroll(expenseMonth));
    }
}
