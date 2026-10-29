package com.salonplatform.dto.offerposter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class FestiveOfferRequest {

    @NotNull
    private UUID branchId;

    /** A {@code PosterOccasion} name. */
    @NotBlank
    private String occasion;

    @NotBlank
    @Size(max = 120)
    private String headline;

    @Size(max = 160)
    private String tagline;

    @NotBlank
    @Size(max = 80)
    private String brandName;

    @Size(max = 200)
    private String address;

    @Size(max = 30)
    private String phone;

    private LocalDate validUntil;

    @Size(max = 400)
    private String terms;

    @NotEmpty
    @Valid
    private List<OfferBlockDto> blocks = new ArrayList<>();

    /** Names for the optional "Salon services include" panel. */
    private List<@Size(max = 60) String> includedServices = new ArrayList<>();

    /** A {@code PosterStyle} name. */
    @NotBlank
    private String style;

    /** A {@code PosterFormat} name. */
    @NotBlank
    private String format;

    @Size(max = 400)
    private String extraDirection;

    /** WhatsApp message. When blank the server writes one from the offer. */
    @Size(max = 2000)
    private String caption;
}
