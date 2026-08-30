package com.salonplatform.service;

import com.salonplatform.domain.entity.Booking;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.notification.Msg91Client;
import com.salonplatform.config.Msg91Properties;
import com.salonplatform.domain.entity.MessageDeliveryLog;
import com.salonplatform.domain.enums.MessageChannel;
import com.salonplatform.domain.enums.MessageDeliveryStatus;
import com.salonplatform.domain.enums.WhatsAppTemplateCode;
import com.salonplatform.domain.repository.MessageDeliveryLogRepository;
import com.salonplatform.util.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentNotificationService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(IST);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).withZone(IST);

    private final Msg91Client msg91Client;
    private final Msg91Properties msg91Properties;
    private final MessageDeliveryLogRepository deliveryLogRepository;
    private final TenantRepository tenantRepository;
    private final WhatsAppTemplateService whatsAppTemplateService;

    public void sendConfirmation(Booking booking, Branch branch, Customer customer, String serviceName, String staffName) {
        String phone = customer.getPhone() != null ? PhoneUtils.normalizeIndianMobile(customer.getPhone()) : null;
        if (phone == null || phone.isBlank()) {
            log.debug("Appointment WhatsApp skipped — no phone on file for booking {}", booking.getId());
            return;
        }

        MessageDeliveryLog deliveryLog = MessageDeliveryLog.builder()
                .tenantId(booking.getTenantId())
                .customerId(customer.getId())
                .channel(MessageChannel.WHATSAPP)
                .recipientPhone(phone)
                .status(MessageDeliveryStatus.PENDING)
                .build();

        if (!Boolean.TRUE.equals(customer.getWhatsappOptIn())) {
            deliveryLog.setStatus(MessageDeliveryStatus.SKIPPED);
            deliveryLog.setErrorMessage("Customer opted out of WhatsApp");
            deliveryLogRepository.save(deliveryLog);
            return;
        }

        Instant start = booking.getScheduledStartAt() != null ? booking.getScheduledStartAt() : Instant.now();
        String branchLabel = resolveBrandName(booking.getTenantId()) + " \u00b7 " + branch.getName();
        List<Map<String, Object>> components = new ArrayList<>();
        components.add(bodyParams(
                customer.getName() != null ? customer.getName() : "Guest",
                branchLabel,
                DATE_FMT.format(start),
                TIME_FMT.format(start),
                serviceName != null ? serviceName : "Salon service"
        ));

        if (!msg91Properties.isEnabled()) {
            deliveryLog.setStatus(MessageDeliveryStatus.SKIPPED);
            deliveryLog.setErrorMessage("MSG91 not configured");
            deliveryLogRepository.save(deliveryLog);
            log.info("Appointment WhatsApp skipped (MSG91 off) for booking {}", booking.getId());
            return;
        }

        if (!whatsAppTemplateService.isActive(booking.getTenantId(), branch.getId(), WhatsAppTemplateCode.APPOINTMENT_CONFIRMED)) {
            deliveryLog.setStatus(MessageDeliveryStatus.SKIPPED);
            deliveryLog.setErrorMessage("Appointment confirmation WhatsApp template is disabled for this salon");
            deliveryLogRepository.save(deliveryLog);
            return;
        }

        Msg91Client.Msg91SendResult result = msg91Client.sendWhatsAppTemplate(
                phone,
                whatsAppTemplateService.resolveTemplateName(WhatsAppTemplateCode.APPOINTMENT_CONFIRMED),
                components);

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
        deliveryLogRepository.save(deliveryLog);
    }

    private String resolveBrandName(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(Tenant::getName)
                .filter(n -> n != null && !n.isBlank())
                .orElse("Salon");
    }

    private static Map<String, Object> bodyParams(String... values) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (String value : values) {
            params.add(Map.of("type", "text", "text", value != null ? value : ""));
        }
        return Map.of("type", "body", "parameters", params);
    }
}
