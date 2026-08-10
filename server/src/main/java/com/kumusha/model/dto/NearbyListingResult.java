package com.kumusha.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.With;

/**
 * DTO for a geospatial proximity search result.
 *
 * <p>Produced by a {@code $geoNear} pipeline over the 2dsphere index on
 * {@code address.location}. This has no equivalent in a movie catalogue: it is the capability
 * a location-bearing dataset unlocks.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record NearbyListingResult (

    /**
     * Listing id.
     */
    String _id,

    /**
     * Listing name.
     */
    String name,

    /**
     * Property type.
     */
    String propertyType,

    /**
     * Room type.
     */
    String roomType,

    /**
     * Nightly price.
     */
    BigDecimal price,

    /**
     * Main image URL.
     */
    String pictureUrl,

    /**
     * Overall review score on the dataset's 0-100 scale.
     */
    Integer reviewScore,

    /**
     * Number of guests accommodated.
     */
    Integer accommodates,

    /**
     * Market / city the listing sits in.
     */
    String market,

    /**
     * Distance from the query point, in metres, as computed by {@code $geoNear}.
     */
    @With
    Double distanceMeters) {}
