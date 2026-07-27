package com.salonplatform.sales.domain;

import java.util.List;

/** Predefined product use cases for field sales qualification. */
public final class SalesUseCases {

    public static final List<String> PREDEFINED = List.of(
            "P&L tracking",
            "WhatsApp campaigns",
            "Inventory management",
            "Staff scheduling",
            "Customer CRM",
            "Online bookings",
            "Multi-branch reporting",
            "Payment reconciliation",
            "Marketing automation",
            "Loyalty programs"
    );

    private SalesUseCases() {}
}
