package com.salonplatform.service;

import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillReceiptNotificationService {

    private final NotificationService notificationService;

    @Async
    public void sendAfterPayment(Invoice invoice, Customer customer) {
        try {
            notificationService.sendBillReceipt(invoice, customer);
        } catch (Exception ex) {
            log.error("Failed to send bill receipt for invoice {}: {}", invoice.getId(), ex.getMessage());
        }
    }
}
