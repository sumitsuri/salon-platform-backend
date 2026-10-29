package com.salonplatform.offerposter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.config.OfferPosterProperties;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

class PosterProvidersTest {

    private PosterImageProviders providers(String mode, String key, boolean allowPreview) {
        OfferPosterProperties p = new OfferPosterProperties();
        p.setProvider(mode);
        p.setApiKey(key);
        p.setAllowPreview(allowPreview);
        return new PosterImageProviders(p, new ObjectMapper());
    }

    @Test
    void autoPrefersTheRealServiceThenThePreviewThenNothing() {
        assertEquals("openai", providers("auto", "sk-x", true).activeName());
        assertEquals("openai", providers("auto", "sk-x", false).activeName());
        assertEquals("preview", providers("auto", "", true).activeName());
        assertEquals("none", providers("auto", "", false).activeName());
        assertEquals("none", providers("auto", null, false).activeName());
    }

    @Test
    void explicitModesAreRespected() {
        assertEquals("none", providers("none", "sk-x", true).activeName());
        assertEquals("none", providers("openai", "", true).activeName());
        assertEquals("preview", providers("preview", "sk-x", true).activeName());
        assertEquals("none", providers("preview", "sk-x", false).activeName());
    }

    @Test
    void previewDrawsAJpegOfTheRequestedSize() throws Exception {
        OfferContent c = OfferCopyTest.content(
                OfferCopyTest.spend(OfferCopyTest.tier("999", "10", null), OfferCopyTest.tier("1999", "15", null)),
                OfferCopyTest.free(OfferCopyTest.tier("2999", null, "Nail Art on 10 Fingers")));
        PosterImageProvider.GeneratedImage img = new PreviewPosterImageProvider().generate("ignored", c);
        var decoded = ImageIO.read(new ByteArrayInputStream(img.bytes()));
        assertEquals(1024, decoded.getWidth());
        assertEquals(1536, decoded.getHeight());
        assertEquals("image/jpeg", img.contentType());
    }
}
