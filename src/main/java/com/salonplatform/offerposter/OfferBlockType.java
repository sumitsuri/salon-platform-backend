package com.salonplatform.offerposter;

/** The three kinds of festive offer an owner can put on a poster. */
public enum OfferBlockType {
    /** "Spend ₹X+ and get Y% off" — one or more spend tiers. */
    SPEND_PERCENT,
    /** A bundle of services at a percentage off the combined price. */
    PACKAGE,
    /** "Spend ₹X+ and get a complimentary service/gift" — one or more spend tiers. */
    FREE_WITH_SPEND
}
