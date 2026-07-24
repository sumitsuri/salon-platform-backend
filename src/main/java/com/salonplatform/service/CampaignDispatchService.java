package com.salonplatform.service;

import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.MarketingCampaign;
import com.salonplatform.domain.enums.CampaignStatus;
import com.salonplatform.domain.enums.MessageDeliveryStatus;
import com.salonplatform.domain.repository.MarketingCampaignRepository;
import com.salonplatform.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignDispatchService {

    private final MarketingCampaignRepository campaignRepository;
    private final NotificationService notificationService;

    @Async
    public void dispatch(UUID campaignId, List<Customer> recipients) {
        MarketingCampaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) {
            return;
        }

        int sent = 0;
        int failed = 0;

        for (Customer customer : recipients) {
            try {
                var deliveryLog = notificationService.sendCampaignMessage(
                        campaign.getTenantId(),
                        campaign.getId(),
                        customer,
                        campaign.getChannel(),
                        campaign.getMessageText());

                if (deliveryLog.getStatus() == MessageDeliveryStatus.SENT) {
                    sent++;
                } else if (deliveryLog.getStatus() == MessageDeliveryStatus.FAILED) {
                    failed++;
                }
            } catch (Exception ex) {
                failed++;
                log.warn("Campaign {} failed for customer {}: {}", campaignId, customer.getId(), ex.getMessage());
            }
        }

        campaign.setSentCount(sent);
        campaign.setFailedCount(failed);
        campaign.setStatus(failed == recipients.size() && sent == 0 && !recipients.isEmpty()
                ? CampaignStatus.FAILED
                : CampaignStatus.COMPLETED);
        campaign.setSentAt(Instant.now());
        campaignRepository.save(campaign);
    }
}
