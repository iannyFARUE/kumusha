package com.kumusha.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;

/**
 * DTO holding the filter values the listings UI needs in a single round trip.
 *
 * <p>The individual {@code distinct()} demonstrations remain available on their own endpoints;
 * this record exists so the filter bar does not have to make four separate calls on mount.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record ListingFacetsResult (

    /**
     * Distinct property types, sorted alphabetically.
     */
    List<String> propertyTypes,

    /**
     * Distinct room types, sorted alphabetically.
     */
    List<String> roomTypes,

    /**
     * Distinct markets (cities), sorted alphabetically.
     */
    List<String> markets,

    /**
     * Lowest nightly price present in the collection.
     */
    Double minPrice,

    /**
     * Highest nightly price present in the collection.
     */
    Double maxPrice) {}
