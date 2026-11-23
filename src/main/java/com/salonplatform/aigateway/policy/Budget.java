package com.salonplatform.aigateway.policy;

/**
 * What one subject (a business and its users) may spend. A value of zero or less switches that limit off.
 *
 * @param dailyRequestsPerUser       questions one person may ask per day
 * @param dailyRequestsPerTenant     questions a whole business may ask per day
 * @param dailySpendPerTenantInr     model spend per business per day, in rupees
 * @param monthlySpendPerTenantInr   model spend per business per calendar month, in rupees
 * @param violationsBeforeCooldown   blocked attempts (off-topic, secrets, injection) within an hour before a pause
 * @param cooldownMinutes            how long that pause lasts
 */
public record Budget(
        int dailyRequestsPerUser,
        int dailyRequestsPerTenant,
        double dailySpendPerTenantInr,
        double monthlySpendPerTenantInr,
        int violationsBeforeCooldown,
        int cooldownMinutes) {

    public static Budget unlimited() {
        return new Budget(0, 0, 0, 0, 0, 0);
    }
}
