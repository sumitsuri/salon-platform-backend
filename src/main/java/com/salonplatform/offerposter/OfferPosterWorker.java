package com.salonplatform.offerposter;

import com.salonplatform.domain.entity.FestiveOffer;
import com.salonplatform.domain.entity.OfferPoster;
import com.salonplatform.domain.enums.OfferPosterStatus;
import com.salonplatform.domain.repository.FestiveOfferRepository;
import com.salonplatform.domain.repository.OfferPosterRepository;
import com.salonplatform.service.OfferPosterStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Runs the slow image call off the request thread and records the outcome on the poster row. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OfferPosterWorker {

    private final OfferPosterRepository posterRepository;
    private final FestiveOfferRepository offerRepository;
    private final PosterImageProviders providers;
    private final FestiveOfferMapper mapper;
    private final OfferPosterStorageService storage;

    @Async("offerPosterExecutor")
    public void generate(UUID posterId) {
        OfferPoster poster = posterRepository.findById(posterId).orElse(null);
        if (poster == null || poster.getStatus() != OfferPosterStatus.PENDING) {
            return;
        }
        try {
            FestiveOffer offer = offerRepository.findById(poster.getOfferId())
                    .orElseThrow(() -> new PosterGenerationException("This offer no longer exists."));
            PosterImageProvider provider = providers.active()
                    .orElseThrow(() -> new PosterGenerationException("Poster generation is not set up on this server."));
            OfferContent content = mapper.toContent(offer);
            PosterImageProvider.GeneratedImage image = provider.generate(poster.getPrompt(), content);
            String key = storage.store(poster.getTenantId(), poster.getOfferId(), poster.getId(),
                    image.bytes(), image.contentType());
            poster.setImageKey(key);
            poster.setContentType(image.contentType());
            poster.setStatus(OfferPosterStatus.READY);
            poster.setError(null);
            poster.setCompletedAt(Instant.now());
            posterRepository.save(poster);
        } catch (PosterGenerationException e) {
            fail(poster, e.getMessage());
        } catch (Exception e) {
            log.error("Poster {} failed unexpectedly", posterId, e);
            fail(poster, "Something went wrong while creating the poster. Please try again.");
        }
    }

    private void fail(OfferPoster poster, String message) {
        poster.setStatus(OfferPosterStatus.FAILED);
        poster.setError(message != null && message.length() > 400 ? message.substring(0, 400) : message);
        poster.setCompletedAt(Instant.now());
        posterRepository.save(poster);
    }
}
