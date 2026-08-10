package com.kumusha.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.With;

/**
 * DTO for the amenity statistics aggregation result.
 *
 * <p>This record represents the result of the reportingByAmenities aggregation, which flattens
 * the {@code amenities} array with {@code $unwind} and then groups by individual amenity.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record AmenityStatisticsResult (

    /**
     * The amenity, e.g. "Wifi".
     */
    String amenity,

    /**
     * Number of listings offering this amenity.
     */
    Integer listingCount,

    /**
     * Average nightly price of listings offering this amenity.
     */
    @With
    Double averagePrice,

    /**
     * Average overall review score of listings offering this amenity.
     */
    @With
    Double averageRating) {}
