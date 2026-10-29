package com.salonplatform.offerposter;

/** Poster shapes. Sizes are the ones the OpenAI image API accepts. */
public enum PosterFormat {
    PORTRAIT("Portrait (best for WhatsApp)", "1024x1536", 1024, 1536),
    SQUARE("Square", "1024x1024", 1024, 1024);

    private final String label;
    private final String size;
    private final int width;
    private final int height;

    PosterFormat(String label, String size, int width, int height) {
        this.label = label;
        this.size = size;
        this.width = width;
        this.height = height;
    }

    public String label() { return label; }
    public String size() { return size; }
    public int width() { return width; }
    public int height() { return height; }
}
