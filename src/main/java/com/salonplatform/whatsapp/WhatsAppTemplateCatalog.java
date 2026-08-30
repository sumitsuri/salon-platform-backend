package com.salonplatform.whatsapp;

import com.salonplatform.config.Msg91Properties;
import com.salonplatform.domain.enums.WhatsAppTemplateCategory;
import com.salonplatform.domain.enums.WhatsAppTemplateCode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Platform catalog of Meta-approved WhatsApp templates (Antrahq WABA).
 * <p>
 * Industry pattern (Zenoti, Fresha, etc.): template copy is fixed and approved once at the
 * platform level; tenants only enable/disable triggers and preview with sample data — they do
 * not edit free-form message bodies (which would require per-tenant Meta re-approval).
 */
public final class WhatsAppTemplateCatalog {

    private static List<WhatsAppTemplateDefinition> definitions;

    private WhatsAppTemplateCatalog() {}

    public static void initialize(Msg91Properties props) {
        definitions = List.of(
                billReceipt(props.getBillReceiptTemplate()),
                promoCampaign(props.getPromoTemplate()),
                appointmentConfirmed(props.getAppointmentConfirmedTemplate()),
                appointmentReminder(),
                billWithFeedback(),
                signupOtp(),
                accountRegistered(),
                rewardPointsEarned(),
                rewardPointsRedeemed(),
                membershipExpiryReminder(),
                packageExpiryReminder(),
                serviceReminder(),
                couponOffer(),
                customerReferral(),
                employeeAppointmentNotify()
        );
    }

    public static List<WhatsAppTemplateDefinition> all() {
        ensureLoaded();
        return definitions;
    }

    public static Optional<WhatsAppTemplateDefinition> find(WhatsAppTemplateCode code) {
        ensureLoaded();
        return definitions.stream().filter(d -> d.getCode() == code).findFirst();
    }

    public static Map<WhatsAppTemplateCode, WhatsAppTemplateDefinition> byCode() {
        ensureLoaded();
        return definitions.stream()
                .collect(Collectors.toMap(WhatsAppTemplateDefinition::getCode, Function.identity()));
    }

    private static void ensureLoaded() {
        if (definitions == null) {
            throw new IllegalStateException("WhatsAppTemplateCatalog not initialized");
        }
    }

    private static WhatsAppTemplateDefinition billReceipt(String templateName) {
        return WhatsAppTemplateDefinition.builder()
                .code(WhatsAppTemplateCode.BILL_RECEIPT)
                .displayName("Bill receipt")
                .msg91TemplateName(templateName)
                .category(WhatsAppTemplateCategory.UTILITY)
                .triggerDescription("After payment at walk-in or booking checkout")
                .bodyTemplate("""
                        Hi {{1}}, thank you for visiting {{2}}! Your invoice {{3}} for Rs.{{4}} is attached.""")
                .displayBody("Dear {FirstName}, Thanks for visiting {CompanyName}. Your payable amount is Rs. {BillTotal}. E-Bill attached.")
                .variables(List.of(
                        var("customerName", "Customer name", 1, "Priya"),
                        var("companyName", "Salon / brand name", 2, "Mystic Wellness"),
                        var("invoiceNumber", "Invoice number", 3, "MW-INV-1042"),
                        var("billTotal", "Bill total (Rs.)", 4, "2,499")
                ))
                .wired(true)
                .defaultActive(true)
                .hasDocumentHeader(true)
                .build();
    }

    private static WhatsAppTemplateDefinition promoCampaign(String templateName) {
        return WhatsAppTemplateDefinition.builder()
                .code(WhatsAppTemplateCode.PROMO_CAMPAIGN)
                .displayName("Marketing promo")
                .msg91TemplateName(templateName)
                .category(WhatsAppTemplateCategory.MARKETING)
                .triggerDescription("Admin campaign send to opted-in customers")
                .bodyTemplate("Hi {{1}}, {{2}} Book your next visit at {{3}} today!")
                .displayBody("Hi {FirstName}, {OfferText} Book your next visit at {CompanyName} today!")
                .variables(List.of(
                        var("customerName", "Customer name", 1, "Amit"),
                        var("offerText", "Offer text (admin enters at send time)", 2, "20% off hair spa this weekend!"),
                        var("companyName", "Salon / brand name", 3, "Mystic Wellness")
                ))
                .wired(true)
                .defaultActive(true)
                .build();
    }

    private static WhatsAppTemplateDefinition appointmentConfirmed(String templateName) {
        return WhatsAppTemplateDefinition.builder()
                .code(WhatsAppTemplateCode.APPOINTMENT_CONFIRMED)
                .displayName("Appointment confirmation")
                .msg91TemplateName(templateName)
                .category(WhatsAppTemplateCategory.UTILITY)
                .triggerDescription("After online booking OTP verification")
                .bodyTemplate("""
                        Hello {{1}},

                        Your appointment at {{2}} is confirmed.

                        Date: {{3}}
                        Time: {{4}}
                        Service: {{5}}

                        Please arrive a few minutes early. To change your appointment, contact the salon.

                        Thank you.""")
                .displayBody("Dear {FirstName}, your appointment is scheduled on {AppointmentDate} {AppointmentTime} at {CompanyName}. Service: {ServiceName}. Thanks.")
                .variables(List.of(
                        var("customerName", "Customer name", 1, "Neha"),
                        var("location", "Brand · branch", 2, "Mystic Wellness · Golden Palms"),
                        var("appointmentDate", "Appointment date", 3, "26 Aug 2026"),
                        var("appointmentTime", "Appointment time", 4, "4:30 PM"),
                        var("serviceName", "Service name", 5, "Hair spa & head massage")
                ))
                .wired(true)
                .defaultActive(true)
                .build();
    }

    private static WhatsAppTemplateDefinition appointmentReminder() {
        return planned(
                WhatsAppTemplateCode.APPOINTMENT_REMINDER,
                "Appointment reminder",
                "antrahq_appointment_reminder_v1",
                WhatsAppTemplateCategory.UTILITY,
                "24 hours before scheduled appointment",
                "Hello {{1}}, reminder: your appointment at {{2}} is on {{3}} at {{4}} for {{5}}. See you soon!",
                "Dear {FirstName}, reminder: your appointment at {CompanyName} is on {AppointmentDate} at {AppointmentTime} for {ServiceName}.",
                List.of(
                        var("customerName", "Customer name", 1, "Rahul"),
                        var("location", "Brand · branch", 2, "Mystic Wellness · Koramangala"),
                        var("appointmentDate", "Appointment date", 3, "27 Aug 2026"),
                        var("appointmentTime", "Appointment time", 4, "11:00 AM"),
                        var("serviceName", "Service name", 5, "Premium haircut")
                ));
    }

    private static WhatsAppTemplateDefinition billWithFeedback() {
        return planned(
                WhatsAppTemplateCode.BILL_WITH_FEEDBACK,
                "Bill with feedback link",
                "antrahq_bill_feedback_v1",
                WhatsAppTemplateCategory.UTILITY,
                "After payment — includes review link",
                "Dear {{1}}, thanks for visiting {{2}}. Your bill total is Rs.{{3}}. E-Bill: {{4}} Review us: {{5}}",
                "Dear {FirstName}, Thanks for visiting {CompanyName}. Your payable amount is Rs. {BillTotal}. E-Bill: {EBillLink}. Review us at {FeedbackLink}",
                List.of(
                        var("customerName", "Customer name", 1, "Sneha"),
                        var("companyName", "Salon / brand name", 2, "Mystic Wellness"),
                        var("billTotal", "Bill total (Rs.)", 3, "1,850"),
                        var("eBillLink", "E-bill link", 4, "https://book.antrahq.com/invoice/…"),
                        var("feedbackLink", "Google review link", 5, "https://g.page/r/…/review")
                ));
    }

    private static WhatsAppTemplateDefinition signupOtp() {
        return planned(
                WhatsAppTemplateCode.SIGNUP_OTP,
                "Signup OTP",
                "antrahq_signup_otp_v1",
                WhatsAppTemplateCategory.AUTHENTICATION,
                "One-time password during online booking / signup",
                "{{1}} is your verification code. For your security, do not share this code.",
                "{OTP} is your verification code. For your security, do not share this code.",
                List.of(var("otp", "OTP code", 1, "482916")));
    }

    private static WhatsAppTemplateDefinition accountRegistered() {
        return planned(
                WhatsAppTemplateCode.ACCOUNT_REGISTERED,
                "Account registration confirmation",
                "antrahq_account_registered_v4",
                WhatsAppTemplateCategory.UTILITY,
                "After salon owner completes Antrahq signup",
                """
                        Hello {{1}},

                        Your Antrahq account registration is complete.

                        Registered email: {{2}}
                        Salon or business name: {{3}}

                        You can sign in with the password you created during signup. If you did not register, please ignore this message.

                        Thank you.""",
                "Hello {FirstName}, Your Antrahq account registration is complete. Email: {Email}. Salon: {CompanyName}. Thank you.",
                List.of(
                        var("firstName", "First name", 1, "Jharna"),
                        var("email", "Registered email", 2, "owner@mystic-wellness.com"),
                        var("companyName", "Salon / business name", 3, "Mystic Wellness")
                ));
    }

    private static WhatsAppTemplateDefinition rewardPointsEarned() {
        return planned(
                WhatsAppTemplateCode.REWARD_POINTS_EARNED,
                "Reward points earned",
                "antrahq_reward_points_earned_v1",
                WhatsAppTemplateCategory.UTILITY,
                "After bill — loyalty points credited",
                "Dear {{1}}, you earned {{2}} reward points on bill {{3}} at {{4}}. Current balance: {{5}}. Expiry: {{6}}. Thanks!",
                "Dear {FirstName}, you earned {PointsEarned} reward points on bill {BillId}. Current balance: {CurrentBalance}. Expiry: {ExpiryDate}.",
                List.of(
                        var("customerName", "Customer name", 1, "Kavya"),
                        var("pointsEarned", "Points earned", 2, "120"),
                        var("billId", "Bill / invoice id", 3, "MW-INV-1042"),
                        var("companyName", "Salon / brand name", 4, "Mystic Wellness"),
                        var("currentBalance", "Points balance", 5, "540"),
                        var("expiryDate", "Points expiry date", 6, "31 Dec 2026")
                ));
    }

    private static WhatsAppTemplateDefinition rewardPointsRedeemed() {
        return planned(
                WhatsAppTemplateCode.REWARD_POINTS_REDEEMED,
                "Reward points redeemed",
                "antrahq_reward_points_redeemed_v1",
                WhatsAppTemplateCategory.UTILITY,
                "When customer redeems loyalty points at billing",
                "Dear {{1}}, {{2}} reward points were used on bill {{3}} at {{4}}. Remaining balance: {{5}}. Thanks!",
                "Dear {FirstName}, {PointsDeducted} reward points were used on bill {BillId}. Remaining balance: {CurrentBalance}.",
                List.of(
                        var("customerName", "Customer name", 1, "Kavya"),
                        var("pointsDeducted", "Points used", 2, "200"),
                        var("billId", "Bill / invoice id", 3, "MW-INV-1050"),
                        var("companyName", "Salon / brand name", 4, "Mystic Wellness"),
                        var("currentBalance", "Points balance", 5, "340")
                ));
    }

    private static WhatsAppTemplateDefinition membershipExpiryReminder() {
        return planned(
                WhatsAppTemplateCode.MEMBERSHIP_EXPIRY_REMINDER,
                "Membership expiry reminder",
                "antrahq_membership_expiry_v1",
                WhatsAppTemplateCategory.UTILITY,
                "Same-day membership expiry reminder",
                "Hi {{1}}, your {{2}} membership at {{3}} expires on {{4}}. Renew today to keep your benefits!",
                "Hi {FirstName}, your {MembershipName} membership at {CompanyName} expires on {ExpiryDate}. Renew today!",
                List.of(
                        var("customerName", "Customer name", 1, "Divya"),
                        var("membershipName", "Membership plan name", 2, "Gold Member"),
                        var("companyName", "Salon / brand name", 3, "Mystic Wellness"),
                        var("expiryDate", "Expiry date", 4, "29 Aug 2026")
                ));
    }

    private static WhatsAppTemplateDefinition packageExpiryReminder() {
        return planned(
                WhatsAppTemplateCode.PACKAGE_EXPIRY_REMINDER,
                "Package expiry reminder",
                "antrahq_package_expiry_v1",
                WhatsAppTemplateCategory.UTILITY,
                "Package / bundle nearing expiry",
                "Hi {{1}}, your {{2}} package at {{3}} expires on {{4}}. {{5}} sessions left. Book now!",
                "Hi {FirstName}, your {PackageName} at {CompanyName} expires on {ExpiryDate}. {RemainingServices} sessions left.",
                List.of(
                        var("customerName", "Customer name", 1, "Ananya"),
                        var("packageName", "Package name", 2, "10-session hair spa"),
                        var("companyName", "Salon / brand name", 3, "Mystic Wellness"),
                        var("expiryDate", "Expiry date", 4, "15 Sep 2026"),
                        var("remainingServices", "Sessions remaining", 5, "3")
                ));
    }

    private static WhatsAppTemplateDefinition serviceReminder() {
        return planned(
                WhatsAppTemplateCode.SERVICE_REMINDER,
                "Service due reminder",
                "antrahq_service_reminder_v1",
                WhatsAppTemplateCategory.MARKETING,
                "Re-engagement when customer is due for a service",
                "Hi {{1}}, it's been a while since your last {{2}} at {{3}}. Book your next visit and stay fresh!",
                "Hi {FirstName}, it's been a while since your last {ServiceName} at {CompanyName}. Book your next visit!",
                List.of(
                        var("customerName", "Customer name", 1, "Meera"),
                        var("serviceName", "Service name", 2, "Hair colour"),
                        var("companyName", "Salon / brand name", 3, "Mystic Wellness")
                ));
    }

    private static WhatsAppTemplateDefinition couponOffer() {
        return planned(
                WhatsAppTemplateCode.COUPON_OFFER,
                "Coupon / offer code",
                "antrahq_coupon_offer_v1",
                WhatsAppTemplateCategory.MARKETING,
                "Promotional coupon issued to customer",
                "Hi {{1}}, your coupon {{2}} gives {{3}} off at {{4}}. Min bill {{5}}. Valid till {{6}}.",
                "Hi {FirstName}, your coupon {CouponCode} gives {DiscountPercent} off at {CompanyName}. Valid till {ExpiryDate}.",
                List.of(
                        var("customerName", "Customer name", 1, "Rohan"),
                        var("couponCode", "Coupon code", 2, "SPA20"),
                        var("discountText", "Discount description", 3, "20%"),
                        var("companyName", "Salon / brand name", 4, "Mystic Wellness"),
                        var("minBillText", "Minimum bill text", 5, "Rs. 999"),
                        var("expiryDate", "Expiry date", 6, "30 Sep 2026")
                ));
    }

    private static WhatsAppTemplateDefinition customerReferral() {
        return planned(
                WhatsAppTemplateCode.CUSTOMER_REFERRAL,
                "Customer referral invite",
                "antrahq_customer_referral_v1",
                WhatsAppTemplateCategory.MARKETING,
                "Refer-a-friend program message",
                "Hi {{1}}, your friend {{2}} invited you to {{3}}. Use code {{4}} for {{5}} off your first visit!",
                "Hi {FirstName}, Your friend {ReferralName} invited you to {CompanyName}. Use code {CouponCode} for {DiscountPercent} off.",
                List.of(
                        var("customerName", "Recipient name", 1, "Friend"),
                        var("referralName", "Referrer name", 2, "Priya"),
                        var("companyName", "Salon / brand name", 3, "Mystic Wellness"),
                        var("couponCode", "Referral coupon code", 4, "FRIEND15"),
                        var("discountText", "Discount description", 5, "15%")
                ));
    }

    private static WhatsAppTemplateDefinition employeeAppointmentNotify() {
        return planned(
                WhatsAppTemplateCode.EMPLOYEE_APPOINTMENT_NOTIFY,
                "Staff appointment alert",
                "antrahq_staff_appointment_v1",
                WhatsAppTemplateCategory.UTILITY,
                "Notify assigned stylist of a new booking",
                "Dear {{1}}, {{2}}: appointment on {{3}} at {{4}} for {{5}} {{6}} ({{7}}).",
                "Dear {EmployeeName}, {CompanyName}: appointment on {AppointmentDate} at {AppointmentTime} for {FirstName} {LastName} ({ServiceName}).",
                List.of(
                        var("employeeName", "Staff name", 1, "Ravi"),
                        var("companyName", "Salon · branch", 2, "Mystic Wellness · Golden Palms"),
                        var("appointmentDate", "Appointment date", 3, "26 Aug 2026"),
                        var("appointmentTime", "Appointment time", 4, "4:30 PM"),
                        var("customerFirstName", "Customer first name", 5, "Neha"),
                        var("customerLastName", "Customer last name", 6, "Sharma"),
                        var("serviceName", "Service name", 7, "Hair spa")
                ));
    }

    private static WhatsAppTemplateDefinition planned(
            WhatsAppTemplateCode code,
            String displayName,
            String msg91Name,
            WhatsAppTemplateCategory category,
            String trigger,
            String bodyTemplate,
            String displayBody,
            List<WhatsAppTemplateVariable> variables) {
        return WhatsAppTemplateDefinition.builder()
                .code(code)
                .displayName(displayName)
                .msg91TemplateName(msg91Name)
                .category(category)
                .triggerDescription(trigger)
                .bodyTemplate(bodyTemplate)
                .displayBody(displayBody)
                .variables(variables)
                .wired(false)
                .defaultActive(false)
                .build();
    }

    private static WhatsAppTemplateVariable var(String key, String label, int metaIndex, String sample) {
        return WhatsAppTemplateVariable.builder()
                .key(key)
                .label(label)
                .metaIndex(metaIndex)
                .sampleValue(sample)
                .build();
    }
}
