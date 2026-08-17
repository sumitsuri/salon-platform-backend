package com.salonplatform.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PasswordResetServiceTest {

    @Test
    void hashTokenIsDeterministic() {
        assertEquals(
                PasswordResetService.hashToken("sample-token"),
                PasswordResetService.hashToken("sample-token"));
    }

    @Test
    void hashTokenDiffersForDifferentInput() {
        assertNotEquals(
                PasswordResetService.hashToken("token-a"),
                PasswordResetService.hashToken("token-b"));
    }
}
