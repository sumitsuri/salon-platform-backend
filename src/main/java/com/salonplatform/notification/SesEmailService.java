package com.salonplatform.notification;

import com.salonplatform.config.SesProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class SesEmailService {

    private final SesProperties properties;
    private SesV2Client sesClient;

    @PostConstruct
    void init() {
        if (properties.isConfigured()) {
            sesClient = SesV2Client.builder()
                    .region(Region.of(properties.getRegion()))
                    .build();
            log.info("SES email enabled: from={}", properties.getFromAddress());
        } else {
            log.info("SES email disabled (set SES_ENABLED=true and SES_FROM_ADDRESS to send mail)");
        }
    }

    public EmailSendResult sendPasswordResetEmail(String toEmail, String userName, String resetUrl) {
        String subject = "Reset your Antrahq password";
        String greeting = userName == null || userName.isBlank() ? "Hi" : "Hi " + userName;
        String textBody = greeting + ",\n\n"
                + "We received a request to reset your Antrahq password.\n\n"
                + "Open this link to choose a new password (valid for 1 hour):\n"
                + resetUrl + "\n\n"
                + "If you did not request this, you can ignore this email.\n\n"
                + "— Antrahq";
        String htmlBody = """
                <p>%s,</p>
                <p>We received a request to reset your Antrahq password.</p>
                <p><a href="%s">Reset your password</a></p>
                <p>This link expires in 1 hour. If you did not request a reset, you can ignore this email.</p>
                <p>— Antrahq</p>
                """.formatted(greeting, resetUrl);

        return send(toEmail, subject, textBody, htmlBody);
    }

    public EmailSendResult send(String toEmail, String subject, String textBody, String htmlBody) {
        if (!properties.isConfigured()) {
            log.info("SES skipped for {} — {}", toEmail, subject);
            return EmailSendResult.skipped("SES not configured");
        }

        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .fromEmailAddress(properties.getFromAddress())
                    .destination(Destination.builder().toAddresses(toEmail).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder().data(subject).charset("UTF-8").build())
                                    .body(Body.builder()
                                            .text(Content.builder().data(textBody).charset("UTF-8").build())
                                            .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                            .build())
                                    .build())
                            .build())
                    .build();

            var response = sesClient.sendEmail(request);
            log.info("SES sent to {} messageId={}", toEmail, response.messageId());
            return EmailSendResult.sent(response.messageId());
        } catch (Exception ex) {
            log.error("SES send failed for {}: {}", toEmail, ex.getMessage());
            return EmailSendResult.failed(ex.getMessage());
        }
    }

    public record EmailSendResult(String status, String detail) {
        public static EmailSendResult sent(String messageId) {
            return new EmailSendResult("SENT", messageId);
        }

        public static EmailSendResult skipped(String reason) {
            return new EmailSendResult("SKIPPED", reason);
        }

        public static EmailSendResult failed(String reason) {
            return new EmailSendResult("FAILED", reason);
        }
    }
}
