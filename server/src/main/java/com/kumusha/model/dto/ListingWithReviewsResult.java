package com.kumusha.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/**
 * DTO for the "most recently reviewed listings" aggregation result.
 *
 * <p>Reviews are embedded inside each listing document in {@code sample_airbnb}, so this
 * aggregation uses {@code $unwind}, {@code $sort}, {@code $group} and {@code $slice} rather
 * than the {@code $lookup} join a separate reviews collection would require.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record ListingWithReviewsResult (

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
     * Market / city the listing sits in.
     */
    String market,

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
     * The most recent reviews for this listing.
     */
    List<ReviewInfo> recentReviews,

    /**
     * Total number of reviews on this listing.
     */
    Integer totalReviews,

    /**
     * Timestamp of the most recent review as a UTC instant.
     */
    Instant mostRecentReviewDate) {

    /**
     * Nested record for a single review summary.
     */
    @Builder
    public record ReviewInfo (
        /**
         * Review id.
         */
        String id,

        /**
         * Name of the guest who left the review.
         */
        String reviewerName,

        /**
         * Body of the review.
         */
        String comments,

        /**
         * Review timestamp as a UTC instant.
         */
        Instant date) {}
}
