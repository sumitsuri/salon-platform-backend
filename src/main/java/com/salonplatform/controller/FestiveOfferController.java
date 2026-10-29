package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.offerposter.FestiveOfferRequest;
import com.salonplatform.dto.offerposter.FestiveOfferResponse;
import com.salonplatform.dto.offerposter.PosterResponse;
import com.salonplatform.dto.offerposter.PosterStudioInfo;
import com.salonplatform.service.FestiveOfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Festive offer posters (brand admin). */
@RestController
@RequestMapping("/api/v1/festive-offers")
@RequiredArgsConstructor
public class FestiveOfferController {

    private final FestiveOfferService service;

    @GetMapping("/studio")
    public ApiResponse<PosterStudioInfo> studio() {
        return ApiResponse.ok(service.studioInfo());
    }

    @GetMapping
    public ApiResponse<List<FestiveOfferResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<FestiveOfferResponse> create(@Valid @RequestBody FestiveOfferRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<FestiveOfferResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<FestiveOfferResponse> update(@PathVariable UUID id, @Valid @RequestBody FestiveOfferRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/posters")
    public ApiResponse<PosterResponse> generate(@PathVariable UUID id) {
        return ApiResponse.ok(service.generatePoster(id));
    }

    @GetMapping("/{id}/posters")
    public ApiResponse<List<PosterResponse>> posters(@PathVariable UUID id) {
        return ApiResponse.ok(service.listPosters(id));
    }

    @GetMapping("/posters/{posterId}")
    public ApiResponse<PosterResponse> poster(@PathVariable UUID posterId) {
        return ApiResponse.ok(service.getPoster(posterId));
    }

    @GetMapping("/posters/{posterId}/image")
    public ResponseEntity<byte[]> posterImage(@PathVariable UUID posterId) {
        FestiveOfferService.PosterImage image = service.loadPosterImage(posterId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(image.bytes());
    }

    @DeleteMapping("/posters/{posterId}")
    public ApiResponse<Void> deletePoster(@PathVariable UUID posterId) {
        service.deletePoster(posterId);
        return ApiResponse.ok(null);
    }
}
