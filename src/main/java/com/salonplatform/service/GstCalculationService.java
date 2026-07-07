package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.DiscountType;
import com.salonplatform.dto.billing.BillPreviewResponse;
import com.salonplatform.dto.billing.BillLinePreview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GstCalculationService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public BillPreviewResponse calculate(Booking booking, List<BookingLineItem> lines) {
        List<BillLinePreview> linePreviews = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;

        for (BookingLineItem line : lines) {
            BigDecimal gross = line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
            BigDecimal lineDiscount = calculateDiscount(gross, line.getLineDiscountType(), line.getLineDiscountValue());
            BigDecimal taxable = gross.subtract(lineDiscount).max(BigDecimal.ZERO);

            BigDecimal halfRate = line.getGstRate().divide(BigDecimal.valueOf(2), 4, ROUNDING);
            BigDecimal cgst = taxable.multiply(halfRate).divide(BigDecimal.valueOf(100), SCALE, ROUNDING);
            BigDecimal sgst = taxable.multiply(halfRate).divide(BigDecimal.valueOf(100), SCALE, ROUNDING);
            BigDecimal lineTotal = taxable.add(cgst).add(sgst);

            linePreviews.add(BillLinePreview.builder()
                    .lineItemId(line.getId())
                    .serviceName(line.getServiceName())
                    .unitPrice(line.getUnitPrice())
                    .quantity(line.getQuantity())
                    .lineDiscount(lineDiscount)
                    .taxableAmount(taxable)
                    .cgstAmount(cgst)
                    .sgstAmount(sgst)
                    .lineTotal(lineTotal)
                    .build());

            subtotal = subtotal.add(taxable);
            totalCgst = totalCgst.add(cgst);
            totalSgst = totalSgst.add(sgst);
        }

        BigDecimal preBillDiscountTotal = subtotal.add(totalCgst).add(totalSgst);
        BigDecimal billDiscount = calculateDiscount(preBillDiscountTotal, booking.getBillDiscountType(), booking.getBillDiscountValue());
        BigDecimal grandTotal = preBillDiscountTotal.subtract(billDiscount).max(BigDecimal.ZERO);

        return BillPreviewResponse.builder()
                .lines(linePreviews)
                .subtotal(subtotal)
                .discountAmount(billDiscount)
                .taxableAmount(subtotal)
                .cgstAmount(totalCgst)
                .sgstAmount(totalSgst)
                .grandTotal(grandTotal)
                .build();
    }

    public BigDecimal calculateDiscount(BigDecimal base, DiscountType type, BigDecimal value) {
        if (type == null || value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (type == DiscountType.FLAT) {
            return value.min(base);
        }
        return base.multiply(value).divide(BigDecimal.valueOf(100), SCALE, ROUNDING);
    }
}
