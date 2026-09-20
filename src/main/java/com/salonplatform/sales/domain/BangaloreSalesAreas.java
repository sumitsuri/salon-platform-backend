package com.salonplatform.sales.domain;

import java.util.List;

/** Major Bangalore localities for field-sales lead discovery (map center coordinates). */
public final class BangaloreSalesAreas {

    private BangaloreSalesAreas() {}

    public record Area(String name, String zone, double latitude, double longitude) {}

    public static final List<Area> ALL = List.of(
            new Area("Koramangala", "South", 12.9352, 77.6245),
            new Area("Indiranagar", "East", 12.9784, 77.6408),
            new Area("HSR Layout", "South", 12.9116, 77.6474),
            new Area("Whitefield", "East", 12.9698, 77.7499),
            new Area("Jayanagar", "South", 12.9250, 77.5938),
            new Area("Malleshwaram", "North", 12.9900, 77.5700),
            new Area("Rajajinagar", "West", 12.9915, 77.5544),
            new Area("MG Road", "Central", 12.9756, 77.6064),
            new Area("Electronic City", "South", 12.8399, 77.6770),
            new Area("Hebbal", "North", 13.0358, 77.5970),
            new Area("Marathahalli", "East", 12.9591, 77.6974),
            new Area("Banashankari", "South", 12.9255, 77.5677),
            new Area("Manyata Tech Park", "North", 13.0475, 77.6197),
            new Area("Sarjapur Road", "East", 12.9023, 77.7848),
            new Area("Bellandur", "East", 12.9260, 77.6762),
            new Area("Yelahanka", "North", 13.1007, 77.5963),
            new Area("BTM Layout", "South", 12.9166, 77.6101),
            new Area("Vijayanagar", "West", 12.9719, 77.5370),
            new Area("Yeshwanthpur", "West", 13.0284, 77.5485),
            new Area("Domlur", "East", 12.9609, 77.6387),
            new Area("Richmond Town", "Central", 12.9654, 77.6012),
            new Area("Frazer Town", "Central", 12.9981, 77.6120),
            new Area("JP Nagar", "South", 12.9068, 77.5850)
    );
}
