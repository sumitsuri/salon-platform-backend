package com.salonplatform.offerposter;

/** Visual looks modelled on the posters owners already make by hand. */
public enum PosterStyle {
    FESTIVE_MAROON_GOLD("Festive maroon & gold", "Rich and celebratory",
            "deep maroon and burgundy background with luminous gold typography, ornate gold borders, warm glowing bokeh lights; "
                    + "offers shown on cream-and-gold arched cards"),
    ROYAL_PURPLE_GOLD("Royal purple & gold", "Premium and dramatic",
            "dark aubergine and plum background with sparkling gold light trails and glitter; offers on glossy white-cream "
                    + "rounded cards with gold price badges and small circular 'discount' seals"),
    CREAM_FLORAL("Cream & floral", "Light, elegant and fresh",
            "soft cream and champagne background with blush and red flowers framing the edges; maroon and gold headings; "
                    + "offers in white rounded cards with maroon section bars"),
    MODERN_MINIMAL("Modern minimal", "Clean and contemporary",
            "clean off-white background with one bold brand colour accent, generous spacing, thin gold lines and simple modern "
                    + "sans-serif typography; offers in simple rounded cards");

    private final String label;
    private final String description;
    private final String direction;

    PosterStyle(String label, String description, String direction) {
        this.label = label;
        this.description = description;
        this.direction = direction;
    }

    public String label() { return label; }
    public String description() { return description; }
    public String direction() { return direction; }
}
