package com.salonplatform.dto.publicbook;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class PublicBookModels {

    private PublicBookModels() {}

    @Data
    @Builder
    public static class ContextResponse {
        private String tenantName;
        private String tenantSlug;
        private String primaryColor;
        private String logoUrl;
        private UUID branchId;
        private String branchName;
        private String branchCode;
        private String address;
        private String phone;
        private String openTime;
        private String closeTime;
        private boolean onlineBookingEnabled;
        private int minLeadMinutes;
        private int maxAdvanceDays;
        private int slotMinutes;
        private String bookBaseUrl;
    }

    @Data
    @Builder
    public static class TenantBranchesResponse {
        private String tenantName;
        private String tenantSlug;
        private String primaryColor;
        private String logoUrl;
        private List<BranchSummary> branches;
    }

    @Data
    @Builder
    public static class BranchSummary {
        private UUID id;
        private String name;
        private String code;
        private String address;
        private String phone;
        private String openTime;
        private String closeTime;
    }

    @Data
    @Builder
    public static class ServiceResponse {
        private UUID branchServiceId;
        private UUID serviceId;
        private String name;
        private UUID categoryId;
        private String categoryName;
        private UUID parentCategoryId;
        private String parentCategoryName;
        private BigDecimal price;
        private Integer durationMinutes;
    }

    @Data
    @Builder
    public static class StaffResponse {
        private UUID id;
        private String name;
        private String skills;
    }

    @Data
    @Builder
    public static class SlotResponse {
        private String startAt;
        private String endAt;
        private UUID staffId;
        private String staffName;
    }

    @Data
    @Builder
    public static class AppointmentResponse {
        private UUID bookingId;
        private String confirmationCode;
        private String manageUrl;
        private String scheduledStartAt;
        private String scheduledEndAt;
        /** Primary label — all services joined for display. */
        private String serviceName;
        private List<String> serviceNames;
        private String staffName;
        private String branchName;
        private String customerName;
    }

    @Data
    @Builder
    public static class OtpResponse {
        private boolean sent;
        private String message;
        private String devOtp;
    }
}
