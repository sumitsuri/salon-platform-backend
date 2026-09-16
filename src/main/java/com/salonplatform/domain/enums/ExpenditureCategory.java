package com.salonplatform.domain.enums;

public enum ExpenditureCategory {
    EMPLOYEE_SALARY,
    RENT,
    PRODUCT_COST,
    EMPLOYEE_ACCOMMODATION_RENT,
    MISCELLANEOUS;

    /** Branch managers may log only operational daily costs; fixed overhead is admin-only. */
    public boolean isManagerRecordable() {
        return this == MISCELLANEOUS;
    }
}
