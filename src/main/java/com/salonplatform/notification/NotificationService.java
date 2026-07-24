package com.salonplatform.notification;

import com.salonplatform.config.Msg91Properties;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.entity.MessageDeliveryLog;
import com.salonplatform.domain.enums.MessageChannel;
import com.salonplatform.domain.enums.MessageDeliveryStatus;
import com.salonplatform.domain.repository.MessageDeliveryLogRepository;
import com.salonplatform.service.InvoiceAccessTokenService;
import com.salonplatform.util.PhoneUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final Msg91Client msg91Client;
    private final Msg91Properties msg91Properties;
    private final MessageDeliveryLogRepository deliveryLogRepository;
    private final InvoiceAccessTokenService invoiceAccessTokenService;

    @Value("${app.public-url:http://localhost:8080}")
    private String publicUrl;

    public MessageDeliveryLog sendBillReceipt(Invoice invoice, Customer customer) {
        String phone = PhoneUtils.normalizeIndianMobile(customer.getPhone());
        MessageDeliveryLog log = MessageDeliveryLog.builder()
                .tenantId(invoice.getTenantId())
                .customerId(customer.getId())
                .invoiceId(invoice.getId())
                .channel(MessageChannel.WHATSAPP)
                .recipientPhone(phone)
                .status(MessageDeliveryStatus.PENDING)
                .build();

        if (!Boolean.TRUE.equals(customer.getWhatsappOptIn())) {
            log.setStatus(MessageDeliveryStatus.SKIPPED);
            log.setErrorMessage("Customer opted out of WhatsApp");
            return deliveryLogRepository.save(log);
        }

        String token = invoiceAccessTokenService.createToken(invoice.getId());
        String pdfUrl = publicUrl + "/api/v1/public/invoices/" + invoice.getId() + "/pdf?token=" + token;
        String filename = invoice.getInvoiceNumber() + ".pdf";

        List<Map<String, Object>> components = new ArrayList<>();
        components.add(headerDocument(pdfUrl, filename));
        components.add(bodyParams(
                customer.getName(),
                invoice.getInvoiceNumber(),
                invoice.getGrandTotal().toPlainString()
        ));

        Msg91Client.Msg91SendResult result = msg91Client.sendWhatsAppTemplate(
                phone,
                msg91Properties.getBillReceiptTemplate(),
                components);

        applyResult(log, result);
        return deliveryLogRepository.save(log);
    }

    public MessageDeliveryLog sendCampaignMessage(
            UUID tenantId,
            UUID campaignId,
            Customer customer,
            MessageChannel channel,
            String messageText) {
        String phone = PhoneUtils.normalizeIndianMobile(customer.getPhone());
        MessageDeliveryLog log = MessageDeliveryLog.builder()
                .tenantId(tenantId)
                .campaignId(campaignId)
                .customerId(customer.getId())
                .channel(channel)
                .recipientPhone(phone)
                .status(MessageDeliveryStatus.PENDING)
                .build();

        if (channel == MessageChannel.WHATSAPP) {
            if (!Boolean.TRUE.equals(customer.getWhatsappOptIn())) {
                log.setStatus(MessageDeliveryStatus.SKIPPED);
                log.setErrorMessage("Customer opted out of WhatsApp");
                return deliveryLogRepository.save(log);
            }
            List<Map<String, Object>> components = new ArrayList<>();
            components.add(bodyParams(customer.getName(), messageText));
            Msg91Client.Msg91SendResult result = msg91Client.sendWhatsAppTemplate(
                    phone,
                    msg91Properties.getPromoTemplate(),
                    components);
            applyResult(log, result);
        } else {
            if (!Boolean.TRUE.equals(customer.getSmsOptIn())) {
                log.setStatus(MessageDeliveryStatus.SKIPPED);
                log.setErrorMessage("Customer opted out of SMS");
                return deliveryLogRepository.save(log);
            }
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("VAR1", customer.getName());
            vars.put("VAR2", messageText);
            Msg91Client.Msg91SendResult result = msg91Client.sendSmsFlow(phone, vars);
            applyResult(log, result);
        }

        return deliveryLogRepository.save(log);
    }

    private void applyResult(MessageDeliveryLog deliveryLog, Msg91Client.Msg91SendResult result) {
        if (result.skipped()) {
            deliveryLog.setStatus(MessageDeliveryStatus.SKIPPED);
            deliveryLog.setErrorMessage(result.error());
        } else if (result.success()) {
            deliveryLog.setStatus(MessageDeliveryStatus.SENT);
            deliveryLog.setProviderMessageId(result.messageId());
        } else {
            deliveryLog.setStatus(MessageDeliveryStatus.FAILED);
            deliveryLog.setErrorMessage(result.error());
        }
    }

    private static Map<String, Object> bodyParams(String... values) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (String value : values) {
            params.add(Map.of("type", "text", "text", value != null ? value : ""));
        }
        return Map.of("type", "body", "parameters", params);
    }

    private static Map<String, Object> headerDocument(String link, String filename) {
        return Map.of(
                "type", "header",
                "parameters", List.of(Map.of(
                        "type", "document",
                        "document", Map.of("link", link, "filename", filename)
                ))
        );
    }
}
