package com.salonplatform.aigateway;

import com.salonplatform.aigateway.model.GatewayContext;
import com.salonplatform.aigateway.policy.Profile;

/**
 * Optional second opinion for a question the deterministic rules cannot place. An application provides one backed by
 * a small, cheap model; with none, the profile's fallback decides.
 */
public interface ScopeClassifier {

    /**
     * @param inScope     true or false when the classifier decided; null when it could not (a timeout, an error)
     * @param model       the model that answered, for audit and pricing
     * @param inputTokens tokens used, so the cost is counted against the business
     */
    record Result(Boolean inScope, String model, long inputTokens, long outputTokens) {
        public static Result unknown() {
            return new Result(null, null, 0, 0);
        }
    }

    Result classify(GatewayContext context, Profile profile, String text);
}
