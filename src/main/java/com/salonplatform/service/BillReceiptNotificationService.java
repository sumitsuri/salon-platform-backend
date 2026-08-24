package com.salonplatform.service;

import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.entity.MessageDeliveryLog;
import com.salonplatform.domain.enums.MessageDeliveryStatus;
import com.salonplatform.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillReceiptNotificationService {

    private final NotificationService notificationService;

    public MessageDeliveryLog sendAfterPayment(Invoice invoice, Customer customer) {
        try {
            return notificationService.sendBillReceipt(invoice, customer);
        } catch (Exception ex) {
            log.error("Failed to send bill receipt for invoice {}: {}", invoice.getId(), ex.getMessage());
            return MessageDeliveryLog.builder()
                    .status(MessageDeliveryStatus.FAILED)
                    .errorMessage(ex.getMessage())
                    .build();
        }
    }
}
