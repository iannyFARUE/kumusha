package com.kumusha.model.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

/**
 * Data Transfer Object for vector search results.
 *
 * <p>This record represents the result of a MongoDB Vector Search query, containing the listing
 * information and its similarity score.
 */
@Builder
public record VectorSearchResult (

    /**
     * Listing id.
     */
    String id,

    /**
     * Listing name.
     */
    String name,

    /**
     * Short summary of the listing.
     */
    String summary,

    /**
     * Main image URL.
     */
    String pictureUrl,

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
     * Market / city the listing sits in.
     */
    String market,

    /**
     * Amenities offered.
     */
    List<String> amenities,

    /**
     * Vector search similarity score (higher = more similar).
     */
    Double score) {}
