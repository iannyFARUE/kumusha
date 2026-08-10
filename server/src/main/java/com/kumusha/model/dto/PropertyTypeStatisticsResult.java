package com.kumusha.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.With;

/**
 * DTO for the property type statistics aggregation result.
 *
 * <p>This record represents the result of the reportingByPropertyType aggregation, which groups
 * listings by property type and computes price and rating statistics with {@code $group}.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PropertyTypeStatisticsResult (

    /**
     * Property type, e.g. "Apartment".
     */
    String propertyType,

    /**
     * Number of listings of this property type.
     */
    Integer listingCount,

    /**
     * Average nightly price.
     */
    @With
    Double averagePrice,

    /**
     * Highest nightly price.
     */
    Double highestPrice,

    /**
     * Lowest nightly price.
     */
    Double lowestPrice,

    /**
     * Average overall review score on the dataset's 0-100 scale.
     */
    @With
    Double averageRating,

    /**
     * Average number of guests accommodated.
     */
    @With
    Double averageAccommodates,

    /**
     * Total number of reviews across all listings of this property type.
     */
    Long totalReviews) {}
