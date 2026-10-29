package com.salonplatform.offerposter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.config.OfferPosterProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Picks which image provider is in use from configuration. */
@Component
public class PosterImageProviders {

    private final OfferPosterProperties props;
    private final PosterImageProvider openAi;
    private final PosterImageProvider preview = new PreviewPosterImageProvider();

    public PosterImageProviders(OfferPosterProperties props, ObjectMapper mapper) {
        this.props = props;
        this.openAi = new OpenAiPosterImageProvider(props, mapper);
    }

    /** The provider posters are generated with right now, or empty when generation is not set up. */
    public Optional<PosterImageProvider> active() {
        String mode = props.getProvider() == null ? "auto" : props.getProvider().trim().toLowerCase();
        boolean hasKey = props.getApiKey() != null && !props.getApiKey().isBlank();
        return switch (mode) {
            case "none" -> Optional.empty();
            case "openai" -> hasKey ? Optional.of(openAi) : Optional.empty();
            case "preview" -> props.isAllowPreview() ? Optional.of(preview) : Optional.empty();
            default -> hasKey ? Optional.of(openAi) : props.isAllowPreview() ? Optional.of(preview) : Optional.empty();
        };
    }

    public String activeName() {
        return active().map(PosterImageProvider::name).orElse("none");
    }
}
