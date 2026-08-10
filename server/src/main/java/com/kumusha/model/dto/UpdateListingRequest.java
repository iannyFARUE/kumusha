package com.kumusha.model.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

/**
 * Data Transfer Object for updating an existing listing.
 *
 * <p>This DTO is used for PATCH /api/listings/{id} requests. All fields are optional since
 * partial updates are allowed; any field left null is not written to the database.
 *
 * <p>Like {@link CreateListingRequest} this record is flat. The service layer translates each
 * property into its MongoDB path, including dotted paths for nested documents such as
 * {@code host.host_name}.
 */
@Builder
public record UpdateListingRequest (

    /**
     * Listing name / headline (optional).
     */
    String name,

    /**
     * Short summary (optional).
     */
    String summary,

    /**
     * Full description (optional).
     */
    String description,

    /**
     * Neighborhood overview (optional).
     */
    String neighborhoodOverview,

    /**
     * Property type (optional).
     */
    String propertyType,

    /**
     * Room type (optional).
     */
    String roomType,

    /**
     * Bed type (optional).
     */
    String bedType,

    /**
     * Cancellation policy (optional).
     */
    String cancellationPolicy,

    /**
     * Maximum number of guests (optional).
     */
    Integer accommodates,

    /**
     * Number of bedrooms (optional).
     */
    Integer bedrooms,

    /**
     * Number of beds (optional).
     */
    Integer beds,

    /**
     * Number of bathrooms (optional).
     */
    BigDecimal bathrooms,

    /**
     * Amenities offered (optional). Replaces the existing array when provided.
     */
    List<String> amenities,

    /**
     * Nightly price (optional).
     */
    BigDecimal price,

    /**
     * Cleaning fee (optional).
     */
    BigDecimal cleaningFee,

    /**
     * Minimum nights per booking (optional).
     */
    String minimumNights,

    /**
     * Maximum nights per booking (optional).
     */
    String maximumNights,

    /**
     * Main listing image URL (optional). Written to {@code images.picture_url}.
     */
    String pictureUrl,

    /**
     * Host display name (optional). Written to {@code host.host_name}.
     */
    String hostName,

    /**
     * Market / city (optional). Written to {@code address.market}.
     */
    String market,

    /**
     * Country (optional). Written to {@code address.country}.
     */
    String country,

    /**
     * Suburb (optional). Written to {@code address.suburb}.
     */
    String suburb) {}
