package com.salonplatform.speech;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Just enough multipart/form-data for one audio file plus a few text fields, so no extra library is needed. */
final class Multipart {
    private final String boundary = "antra" + UUID.randomUUID().toString().replace("-", "");
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    Multipart field(String name, String value) {
        write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
        return this;
    }

    Multipart file(String name, String filename, String mimeType, byte[] data) {
        write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename
                + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n");
        out.writeBytes(data);
        write("\r\n");
        return this;
    }

    byte[] build() {
        write("--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private void write(String s) {
        out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    /** A file name with an extension the engine recognises, from the recording's type. */
    static String filenameFor(String mimeType) {
        String m = mimeType == null ? "" : mimeType.toLowerCase();
        if (m.contains("webm")) return "speech.webm";
        if (m.contains("ogg") || m.contains("opus")) return "speech.ogg";
        if (m.contains("mp4") || m.contains("m4a") || m.contains("aac")) return "speech.m4a";
        if (m.contains("mpeg") || m.contains("mp3")) return "speech.mp3";
        if (m.contains("wav")) return "speech.wav";
        return "speech.bin";
    }
}
