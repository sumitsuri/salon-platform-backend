package com.salonplatform.aigateway.model;

import java.util.List;
import java.util.Set;

/**
 * A conversation about to be sent to a model, and what the gateway needs to judge it.
 *
 * @param channel      which product surface is asking (for example "ask-antra"), used in audit records
 * @param profile      the policy profile to apply; unknown profiles fall back to the strict default
 * @param provider     the model provider (anthropic, bedrock, openai, ...), used for audit and pricing
 * @param model        the model id, used to price the request
 * @param messages     the whole conversation; the last message is the new question
 * @param contextTerms words that belong to this subject's world (for example its branch and staff names); they count
 *                     towards "this message is on topic"
 */
public record GatewayRequest(
        String channel,
        String profile,
        Subject subject,
        String provider,
        String model,
        List<Message> messages,
        Set<String> contextTerms) {}
