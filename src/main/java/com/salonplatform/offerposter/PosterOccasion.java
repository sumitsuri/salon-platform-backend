package com.salonplatform.offerposter;

/**
 * Festive / seasonal themes. {@code scene} is the art direction handed to the image model; the headline and tagline
 * are only suggestions the owner can overwrite.
 */
public enum PosterOccasion {
    DIWALI("Diwali", "🪔", "Diwali Beauty Offers", "Glow, Glam & Celebrate",
            "glowing brass diyas, marigold and rose garlands, golden bokeh fairy lights and rangoli in a warm festive evening setting"),
    KARWA_CHAUTH("Karwa Chauth", "🌙", "Karwa Chauth Beauty Bonus", "Pamper yourself this festive season",
            "a full moon in a twilight sky, a decorated puja thali and sieve, glowing diyas, red and gold bridal-festive mood with mehendi and bangles"),
    NAVRATRI("Navratri / Durga Puja", "🔱", "Navratri Special Offers", "Nine nights of glow",
            "vibrant garba colours, marigold garlands, dandiya sticks, brass diyas and rich red, orange and pink festive fabrics"),
    HOLI("Holi", "🎨", "Holi Glow Offers", "Colour yourself beautiful",
            "bursts of gulal powder in pink, yellow, blue and green, flower petals in the air, joyful bright spring daylight"),
    RAKSHA_BANDHAN("Raksha Bandhan", "🪢", "Rakhi Special Offers", "Treat the sister in your life",
            "decorative rakhis, a puja thali with diya, flowers and sweets in soft warm festive light"),
    EID("Eid", "🌙", "Eid Beauty Offers", "Eid Mubarak — look your best",
            "a crescent moon and stars, lanterns, elegant gold and emerald tones, soft candlelight and fresh flowers"),
    CHRISTMAS_NEW_YEAR("Christmas & New Year", "🎄", "New Year Glow Offers", "Start the year glowing",
            "sparkling lights, champagne-gold bokeh, festive baubles and elegant party glamour"),
    VALENTINES("Valentine's Day", "💝", "Valentine's Day Offers", "Treat yourself and your loved one",
            "soft pink and red roses, hearts, candlelight and a romantic blush-toned setting"),
    WOMENS_DAY("Women's Day", "🌸", "Women's Day Special", "Celebrate you",
            "fresh purple and pink flowers, soft confident feminine mood and elegant light"),
    MOTHERS_DAY("Mother's Day", "💐", "Mother's Day Specials", "Because she deserves it",
            "pastel bouquets, warm natural light and a tender, graceful mood"),
    WEDDING_SEASON("Wedding season", "💍", "Wedding Season Offers", "Be the most beautiful guest — or bride",
            "Indian wedding decor, marigold and rose floral arches, gold jewellery, mehendi and rich bridal fabrics"),
    SUMMER("Summer", "☀️", "Summer Glow Offers", "Beat the heat, keep the glow",
            "fresh tropical flowers, bright sunlit tones, cool aqua and coral accents"),
    MONSOON("Monsoon", "🌧️", "Monsoon Offers", "Rainy days, radiant you",
            "gentle rain droplets, lush green leaves, cosy warm light and fresh jasmine"),
    NEW_LAUNCH("New service launch", "✨", "Now Offering", "Introducing our newest services",
            "an elegant cream and gold backdrop with fresh flowers and a polished, premium studio feel"),
    GENERAL("General offer", "🎁", "Special Offers", "Limited time — book today",
            "an elegant premium salon setting with soft golden light and subtle floral details");

    private final String label;
    private final String emoji;
    private final String suggestedHeadline;
    private final String suggestedTagline;
    private final String scene;

    PosterOccasion(String label, String emoji, String headline, String tagline, String scene) {
        this.label = label;
        this.emoji = emoji;
        this.suggestedHeadline = headline;
        this.suggestedTagline = tagline;
        this.scene = scene;
    }

    public String label() { return label; }
    public String emoji() { return emoji; }
    public String suggestedHeadline() { return suggestedHeadline; }
    public String suggestedTagline() { return suggestedTagline; }
    public String scene() { return scene; }
}
