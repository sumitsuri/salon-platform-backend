package com.salonplatform.offerposter;

/** Image generation failed; the message is safe to show to the owner. */
public class PosterGenerationException extends RuntimeException {
    public PosterGenerationException(String userMessage) {
        super(userMessage);
    }

    public PosterGenerationException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
