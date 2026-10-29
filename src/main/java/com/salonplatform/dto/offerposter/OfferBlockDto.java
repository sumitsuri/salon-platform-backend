package com.salonplatform.dto.offerposter;

import com.salonplatform.offerposter.OfferBlockType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One offer on the poster. Which fields apply depends on {@link #type}. */
@Data
public class OfferBlockDto {

    @NotNull
    private OfferBlockType type;

    /** PACKAGE: the package name, e.g. "Bridal Glow Package". Optional for the other types. */
    private String title;

    /** SPEND_PERCENT / FREE_WITH_SPEND: what the spend counts toward. Defaults to "any salon services". */
    private String appliesTo;

    /** SPEND_PERCENT / FREE_WITH_SPEND. */
    private List<Tier> tiers = new ArrayList<>();

    /** PACKAGE: services in the bundle (price snapshot taken from the catalogue when chosen). */
    private List<PackageItem> items = new ArrayList<>();

    /** PACKAGE: percentage taken off the combined price of the items. */
    private BigDecimal discountPercent;

    @Data
    public static class Tier {
        /** Minimum bill value (₹) that unlocks this tier. */
        private BigDecimal minSpend;
        /** SPEND_PERCENT: percentage off. */
        private BigDecimal percentOff;
        /** FREE_WITH_SPEND: the complimentary service or gift, e.g. "Nail Art on 5 Fingers". */
        private String freeItem;
    }

    @Data
    public static class PackageItem {
        private UUID serviceId;
        private String name;
        private BigDecimal price;
    }
}
