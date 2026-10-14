package com.salonplatform.exception;

/**
 * The caller's session is no longer valid (revoked, signed out elsewhere, account disabled). Mapped to HTTP 401 so
 * clients can tell "your session ended" apart from a malformed request or a server fault.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
