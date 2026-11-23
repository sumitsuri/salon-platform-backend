package com.salonplatform.aigateway.model;

import java.util.List;

/** The verdict on a model's answer before it reaches the person. */
public record OutboundDecision(Action action, String text, List<Finding> findings) {

    /** True when the text differs from what the model wrote. */
    public boolean changed() {
        return action != Action.ALLOW;
    }
}
