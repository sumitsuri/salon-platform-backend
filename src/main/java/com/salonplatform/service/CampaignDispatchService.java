package com.salonplatform.service;

import com.salonplatform.domain.entity.CampaignRun;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.MarketingCampaign;
import com.salonplatform.domain.enums.CampaignRunStatus;
import com.salonplatform.domain.enums.CampaignStatus;
import com.salonplatform.domain.repository.CampaignRunRepository;
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
    private final CampaignRunRepository runRepository;
    private final NotificationService notificationService;

    @Async
    public void dispatch(UUID campaignId, UUID runId, List<Customer> recipients) {
        MarketingCampaign campaign = campaignRepository.findById(campaignId).orElse(null);
        CampaignRun run = runRepository.findById(runId).orElse(null);
        if (campaign == null || run == null) {
            return;
        }

        int sent = 0;
        int failed = 0;
        int skipped = 0;

        for (Customer customer : recipients) {
            try {
                var deliveryLog = notificationService.sendCampaignMessage(
                        campaign.getTenantId(),
                        campaign.getId(),
                        runId,
                        customer,
                        campaign.getChannel(),
                        campaign.getMessageText());

                switch (deliveryLog.getStatus()) {
                    case SENT -> sent++;
                    case FAILED -> failed++;
                    default -> skipped++;
                }
            } catch (Exception ex) {
                failed++;
                log.warn("Campaign {} run {} failed for customer {}: {}",
                        campaignId, runId, customer.getId(), ex.getMessage());
            }
        }

        run.setSentCount(sent);
        run.setFailedCount(failed + skipped);
        run.setStatus(failed == recipients.size() && sent == 0 && !recipients.isEmpty()
                ? CampaignRunStatus.FAILED
                : CampaignRunStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        runRepository.save(run);

        campaign.setSentCount(campaign.getSentCount() + sent);
        campaign.setFailedCount(campaign.getFailedCount() + failed + skipped);
        campaign.setRecipientCount(run.getRecipientCount());
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setSentAt(Instant.now());
        campaignRepository.save(campaign);
    }
}
