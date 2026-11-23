package com.salonplatform.aigateway.model;

/** One turn of a conversation, as plain text. */
public record Message(Role role, String text) {

    public enum Role { USER, ASSISTANT }

    public static Message user(String text) {
        return new Message(Role.USER, text);
    }

    public static Message assistant(String text) {
        return new Message(Role.ASSISTANT, text);
    }
}
