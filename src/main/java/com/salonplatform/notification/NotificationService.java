package com.salonplatform.notification;

import com.salonplatform.config.Msg91Properties;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.entity.MessageDeliveryLog;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.enums.MessageChannel;
import com.salonplatform.domain.enums.MessageDeliveryStatus;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.MessageDeliveryLogRepository;
import com.salonplatform.domain.repository.TenantRepository;
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
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;

    @Value("${app.api-public-url:http://localhost:8080}")
    private String apiPublicUrl;

    public MessageDeliveryLog sendBillReceipt(Invoice invoice, Customer customer) {
        String phone = customer.getPhone() != null ? PhoneUtils.normalizeIndianMobile(customer.getPhone()) : null;
        MessageDeliveryLog log = MessageDeliveryLog.builder()
                .tenantId(invoice.getTenantId())
                .customerId(customer.getId())
                .invoiceId(invoice.getId())
                .channel(MessageChannel.WHATSAPP)
                .recipientPhone(phone != null ? phone : "")
                .status(MessageDeliveryStatus.PENDING)
                .build();

        if (phone == null || phone.isBlank()) {
            log.setStatus(MessageDeliveryStatus.SKIPPED);
            log.setErrorMessage("Customer has no phone on file");
            return deliveryLogRepository.save(log);
        }

        if (!isWhatsappOptIn(customer)) {
            log.setStatus(MessageDeliveryStatus.SKIPPED);
            log.setErrorMessage("Customer opted out of WhatsApp");
            return deliveryLogRepository.save(log);
        }

        if (!isBillReceiptAllowedForBranch(invoice)) {
            log.setStatus(MessageDeliveryStatus.SKIPPED);
            log.setErrorMessage("WhatsApp bill receipt pilot — enabled only for "
                    + msg91Properties.billReceiptPilotLabel());
            return deliveryLogRepository.save(log);
        }

        String token = invoiceAccessTokenService.createToken(invoice.getId());
        String pdfUrl = apiPublicUrl + "/api/v1/public/invoices/" + invoice.getId() + "/pdf?token=" + token;
        String filename = invoice.getInvoiceNumber() + ".pdf";

        List<Map<String, Object>> components = new ArrayList<>();
        components.add(headerDocument(pdfUrl, filename));
        components.add(bodyParams(
                customer.getName(),
                resolveBrandName(invoice.getTenantId()),
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
        String phone = customer.getPhone() != null ? PhoneUtils.normalizeIndianMobile(customer.getPhone()) : null;
        MessageDeliveryLog log = MessageDeliveryLog.builder()
                .tenantId(tenantId)
                .campaignId(campaignId)
                .customerId(customer.getId())
                .channel(channel)
                .recipientPhone(phone != null ? phone : "")
                .status(MessageDeliveryStatus.PENDING)
                .build();

        if (phone == null || phone.isBlank()) {
            log.setStatus(MessageDeliveryStatus.SKIPPED);
            log.setErrorMessage("Customer has no phone on file");
            return deliveryLogRepository.save(log);
        }

        if (channel == MessageChannel.WHATSAPP) {
            if (!isWhatsappOptIn(customer)) {
                log.setStatus(MessageDeliveryStatus.SKIPPED);
                log.setErrorMessage("Customer opted out of WhatsApp");
                return deliveryLogRepository.save(log);
            }
            List<Map<String, Object>> components = new ArrayList<>();
            components.add(bodyParams(
                    customer.getName(),
                    messageText,
                    resolveBrandName(tenantId)));
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
            vars.put("VAR3", resolveBrandName(tenantId));
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

    private String resolveBrandName(UUID tenantId) {
        if (tenantId == null) {
            return "your salon";
        }
        return tenantRepository.findById(tenantId)
                .map(Tenant::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("your salon");
    }

    private boolean isWhatsappOptIn(Customer customer) {
        Boolean optIn = customer.getWhatsappOptIn();
        if (optIn == null) {
            return customer.getPhone() != null && !customer.getPhone().isBlank();
        }
        return Boolean.TRUE.equals(optIn);
    }

    private boolean isBillReceiptAllowedForBranch(Invoice invoice) {
        if (!msg91Properties.isBillReceiptPilotEnabled()) {
            return true;
        }
        var branch = branchRepository.findById(invoice.getBranchId()).orElse(null);
        var tenant = tenantRepository.findById(invoice.getTenantId()).orElse(null);
        if (branch == null || tenant == null || tenant.getSlug() == null || branch.getCode() == null) {
            return false;
        }
        return msg91Properties.allowsBillReceiptFor(tenant.getSlug(), branch.getCode());
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
