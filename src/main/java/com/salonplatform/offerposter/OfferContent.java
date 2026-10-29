package com.salonplatform.offerposter;

import com.salonplatform.dto.offerposter.OfferBlockDto;

import java.time.LocalDate;
import java.util.List;

/** Everything the caption and prompt builders need, independent of how the offer is stored. */
public record OfferContent(
        PosterOccasion occasion,
        PosterStyle style,
        PosterFormat format,
        String brandName,
        String headline,
        String tagline,
        String address,
        String phone,
        LocalDate validUntil,
        String terms,
        List<OfferBlockDto> blocks,
        List<String> includedServices,
        String extraDirection) {
}
