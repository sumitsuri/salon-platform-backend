package com.salonplatform.speech;

import java.math.BigDecimal;

/**
 * A speech-to-text engine that Antra calls from its own servers. The browser's built-in recogniser is free and is tried
 * first; an engine here is for phones and app-store builds where that is missing or not accurate enough, and is only
 * used when switched on in settings. Audio is passed straight through: nothing implementing this may keep it.
 */
public interface SpeechToText {

    /** Short stable name written to the audit trail and to feedback, e.g. {@code sarvam}. */
    String id();

    /** Model name, for the audit trail. */
    String model();

    /** Rupees per minute of audio, used to count the call against the business's spend limit. */
    BigDecimal costInrPerMinute();

    /**
     * @param audio    one short recording, at most a few hundred kilobytes
     * @param mimeType e.g. {@code audio/webm}
     * @param language a BCP-47 tag such as {@code hi-IN}, or {@code en-IN} for English and Hinglish
     */
    String transcribe(byte[] audio, String mimeType, String language) throws SpeechException;

    /** The engine failed or refused; the message is safe to show a person, the cause is for logs only. */
    class SpeechException extends Exception {
        public SpeechException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
