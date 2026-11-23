package com.salonplatform.forecast;

import com.salonplatform.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Expected sales and stock readiness (brand admin: everything; branch manager: their own branch). */
@RestController
@RequestMapping("/api/v1/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final SalesForecastService sales;
    private final StockReadinessService stock;

    @GetMapping("/sales")
    public ApiResponse<SalesForecastService.ForecastResponse> sales(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(sales.forecast(days));
    }

    @GetMapping("/stock-readiness")
    public ApiResponse<StockReadinessService.Readiness> stockReadiness(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(stock.readiness(days));
    }
}
