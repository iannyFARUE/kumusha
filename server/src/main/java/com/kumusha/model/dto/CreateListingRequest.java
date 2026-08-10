package com.kumusha.model.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

/**
 * Data Transfer Object for creating a new listing.
 *
 * <p>This DTO is used for POST /api/listings requests. It includes validation annotations to
 * ensure required fields are present. Only the name field is required; all other fields are
 * optional.
 *
 * <p>The request is deliberately flat: nested document fields such as {@code host.host_name}
 * and {@code address.market} are supplied as top-level properties and assembled into the
 * nested structure by the service layer.
 */
@Builder
public record CreateListingRequest (

    /**
     * Listing name / headline (required).
     * Must not be blank.
     */
    @NotBlank(message = "Name is required")
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
     * Property type, e.g. "Apartment" (optional).
     */
    String propertyType,

    /**
     * Room type, e.g. "Entire home/apt" (optional).
     */
    String roomType,

    /**
     * Bed type, e.g. "Real Bed" (optional).
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
     * Amenities offered (optional).
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
     * Minimum nights per booking (optional). Stored as a string to match the dataset.
     */
    String minimumNights,

    /**
     * Maximum nights per booking (optional). Stored as a string to match the dataset.
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
    String suburb,

    /**
     * Longitude of the listing (optional). Must be supplied together with latitude to build
     * the GeoJSON point used by proximity search.
     */
    Double longitude,

    /**
     * Latitude of the listing (optional). Must be supplied together with longitude.
     */
    Double latitude) {}
