package com.salonplatform.offerposter;

/** Something that can turn a poster prompt into an image. */
public interface PosterImageProvider {

    /** Short id stored on the poster: "openai" or "preview". */
    String name();

    String model();

    GeneratedImage generate(String prompt, OfferContent content) throws PosterGenerationException;

    record GeneratedImage(byte[] bytes, String contentType) {}
}
