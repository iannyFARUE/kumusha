package com.kumusha.model.dto;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * Data Transfer Object for listing query parameters.
 *
 * <p>This DTO is used to parse and validate query parameters for GET /api/listings requests.
 * It supports full-text search, filtering by property type, room type, market, amenity, price
 * range, capacity and rating, plus sorting and pagination.
 */
@Builder
public record ListingSearchQuery (

    /**
     * Full-text search query.
     * Searches across name, summary and description using the MongoDB text index.
     */
    String q,

    /**
     * Filter by property type (case-insensitive exact match), e.g. "Apartment".
     */
    String propertyType,

    /**
     * Filter by room type (case-insensitive exact match), e.g. "Entire home/apt".
     */
    String roomType,

    /**
     * Filter by market / city (case-insensitive exact match), e.g. "Porto".
     */
    String market,

    /**
     * Filter by country (case-insensitive exact match).
     */
    String country,

    /**
     * Filter to listings offering this amenity (case-insensitive exact match), e.g. "Wifi".
     */
    String amenity,

    /**
     * Minimum nightly price (inclusive).
     */
    BigDecimal minPrice,

    /**
     * Maximum nightly price (inclusive).
     */
    BigDecimal maxPrice,

    /**
     * Minimum number of bedrooms (inclusive).
     */
    Integer minBedrooms,

    /**
     * Minimum number of guests the listing must accommodate (inclusive).
     */
    Integer minAccommodates,

    /**
     * Minimum overall review score, on the dataset's 0-100 scale (inclusive).
     */
    Integer minRating,

    /**
     * Only return listings hosted by a superhost.
     */
    Boolean superhostOnly,

    /**
     * Number of results to return (default: 20, max: 100).
     */
    Integer limit,

    /**
     * Number of results to skip for pagination (default: 0).
     */
    Integer skip,

    /**
     * Field to sort by (e.g., "name", "price", "review_scores.review_scores_rating").
     * Default: "name"
     */
    String sortBy,

    /**
     * Sort order: "asc" or "desc".
     * Default: "asc"
     */
    String sortOrder) {}
