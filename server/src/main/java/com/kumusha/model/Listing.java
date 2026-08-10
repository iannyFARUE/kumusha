package com.kumusha.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Domain model representing a stay listing from the MongoDB listingsAndReviews collection.
 *
 * <p>This class maps to the {@code listingsAndReviews} collection in the {@code sample_airbnb}
 * database. Unlike many MongoDB collections, documents here use <strong>string</strong>
 * {@code _id} values (for example {@code "10006546"}) rather than ObjectIds, so the id
 * property is a {@link String} and no ObjectId parsing is performed anywhere in the
 * application.
 *
 * <p>Monetary fields are stored as BSON {@code Decimal128} and are mapped to
 * {@link BigDecimal}. The {@code minimum_nights} and {@code maximum_nights} fields are stored
 * as strings in the source dataset and are mapped as such rather than being coerced.
 *
 * <p><strong>Embeddings are deliberately not mapped.</strong> The vector used by
 * MongoDB Vector Search lives in the {@code description_embedding} field, which holds
 * thousands of doubles per document. Mapping it would balloon every list response, so the
 * service layer reads and writes it through the raw driver instead. This is safe because no
 * code path calls {@code save()} on a Listing loaded from an existing document — updates go
 * through {@code updateFirst} with {@code $set}, which leaves unmapped fields untouched.
 *
 * <p>Note: Lombok annotations reduce boilerplate:
 * - @Getter @Setter @ToString @EqualsAndHashCode: generates getters, setters, toString, equals, hashCode
 * - @Builder: provides a fluent builder for object construction
 */
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@AllArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
@NoArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
@Document(collection = "listingsAndReviews")
public class Listing {

    /**
     * Field name constants for MongoDB operations.
     *
     * <p>These constants should be used when referencing field names in queries, filters,
     * indexes, and other MongoDB operations to ensure type safety and enable IDE
     * "Find Usages" functionality.
     *
     * <p>Example usage:
     * <pre>
     * filter.append(Listing.Fields.PROPERTY_TYPE, "Apartment");
     * Indexes.geo2dsphere(Listing.Fields.ADDRESS_LOCATION);
     * </pre>
     */
    public static class Fields {
        public static final String ID = "_id";
        public static final String LISTING_URL = "listing_url";
        public static final String NAME = "name";
        public static final String SUMMARY = "summary";
        public static final String SPACE = "space";
        public static final String DESCRIPTION = "description";
        public static final String NEIGHBORHOOD_OVERVIEW = "neighborhood_overview";
        public static final String NOTES = "notes";
        public static final String TRANSIT = "transit";
        public static final String ACCESS = "access";
        public static final String INTERACTION = "interaction";
        public static final String HOUSE_RULES = "house_rules";
        public static final String PROPERTY_TYPE = "property_type";
        public static final String ROOM_TYPE = "room_type";
        public static final String BED_TYPE = "bed_type";
        public static final String CANCELLATION_POLICY = "cancellation_policy";
        public static final String ACCOMMODATES = "accommodates";
        public static final String BEDROOMS = "bedrooms";
        public static final String BEDS = "beds";
        public static final String BATHROOMS = "bathrooms";
        public static final String NUMBER_OF_REVIEWS = "number_of_reviews";
        public static final String AMENITIES = "amenities";
        public static final String PRICE = "price";
        public static final String SECURITY_DEPOSIT = "security_deposit";
        public static final String CLEANING_FEE = "cleaning_fee";
        public static final String EXTRA_PEOPLE = "extra_people";
        public static final String GUESTS_INCLUDED = "guests_included";
        public static final String MINIMUM_NIGHTS = "minimum_nights";
        public static final String MAXIMUM_NIGHTS = "maximum_nights";
        public static final String FIRST_REVIEW = "first_review";
        public static final String LAST_REVIEW = "last_review";
        public static final String IMAGES = "images";
        public static final String IMAGES_PICTURE_URL = "images.picture_url";
        public static final String HOST = "host";
        public static final String HOST_ID = "host.host_id";
        public static final String HOST_NAME = "host.host_name";
        public static final String HOST_IS_SUPERHOST = "host.host_is_superhost";
        public static final String ADDRESS = "address";
        public static final String ADDRESS_MARKET = "address.market";
        public static final String ADDRESS_COUNTRY = "address.country";
        public static final String ADDRESS_SUBURB = "address.suburb";
        public static final String ADDRESS_LOCATION = "address.location";
        public static final String AVAILABILITY = "availability";
        public static final String REVIEW_SCORES = "review_scores";
        public static final String REVIEW_SCORES_RATING = "review_scores.review_scores_rating";
        public static final String REVIEWS = "reviews";

        /**
         * Field holding the Voyage AI embedding of the listing description.
         *
         * <p>This field is not present in the stock sample_airbnb dataset. It is created by the
         * embedding backfill endpoint and is intentionally not mapped onto this class.
         */
        public static final String DESCRIPTION_EMBEDDING = "description_embedding";

        private Fields() {
            // Private constructor to prevent instantiation
        }
    }

    /**
     * MongoDB document ID.
     *
     * <p>Maps to the {@code _id} field, which is a string in this dataset.
     * Can be null for new documents (the service assigns one before insert).
     */
    @JsonProperty("_id")
    @Id
    @ToString.Include
    @EqualsAndHashCode.Include
    private String id;

    /**
     * Listing name / headline (required field).
     */
    @ToString.Include
    private String name;

    /**
     * Public URL of the original listing.
     */
    @Field(Fields.LISTING_URL)
    private String listingUrl;

    /**
     * Short summary of the listing.
     */
    private String summary;

    /**
     * Description of the space itself.
     */
    private String space;

    /**
     * Full listing description.
     */
    private String description;

    /**
     * Host's description of the surrounding neighborhood.
     */
    @Field(Fields.NEIGHBORHOOD_OVERVIEW)
    private String neighborhoodOverview;

    /**
     * Additional notes from the host.
     */
    private String notes;

    /**
     * Transit and access information.
     */
    private String transit;

    /**
     * What parts of the property the guest can access.
     */
    private String access;

    /**
     * How the host interacts with guests.
     */
    private String interaction;

    /**
     * House rules set by the host.
     */
    @Field(Fields.HOUSE_RULES)
    private String houseRules;

    /**
     * Property type (e.g., "Apartment", "House", "Condominium").
     */
    @ToString.Include
    @Field(Fields.PROPERTY_TYPE)
    private String propertyType;

    /**
     * Room type (e.g., "Entire home/apt", "Private room", "Shared room").
     */
    @Field(Fields.ROOM_TYPE)
    private String roomType;

    /**
     * Bed type (e.g., "Real Bed", "Futon").
     */
    @Field(Fields.BED_TYPE)
    private String bedType;

    /**
     * Cancellation policy (e.g., "flexible", "moderate", "strict").
     */
    @Field(Fields.CANCELLATION_POLICY)
    private String cancellationPolicy;

    /**
     * Maximum number of guests the listing accommodates.
     */
    private Integer accommodates;

    /**
     * Number of bedrooms.
     */
    private Integer bedrooms;

    /**
     * Number of beds.
     */
    private Integer beds;

    /**
     * Number of bathrooms. Stored as Decimal128 because half-bathrooms are common.
     */
    private BigDecimal bathrooms;

    /**
     * Total number of reviews the listing has received.
     */
    @Field(Fields.NUMBER_OF_REVIEWS)
    private Integer numberOfReviews;

    /**
     * List of amenities (e.g., "Wifi", "Kitchen", "Free parking on premises").
     */
    private List<String> amenities;

    /**
     * Nightly price. Stored as Decimal128 in MongoDB.
     */
    @ToString.Include
    private BigDecimal price;

    /**
     * Refundable security deposit.
     */
    @Field(Fields.SECURITY_DEPOSIT)
    private BigDecimal securityDeposit;

    /**
     * One-off cleaning fee.
     */
    @Field(Fields.CLEANING_FEE)
    private BigDecimal cleaningFee;

    /**
     * Surcharge per additional guest beyond {@code guestsIncluded}.
     */
    @Field(Fields.EXTRA_PEOPLE)
    private BigDecimal extraPeople;

    /**
     * Number of guests included in the base price.
     */
    @Field(Fields.GUESTS_INCLUDED)
    private BigDecimal guestsIncluded;

    /**
     * Minimum nights per booking. Stored as a string in the source dataset.
     */
    @Field(Fields.MINIMUM_NIGHTS)
    private String minimumNights;

    /**
     * Maximum nights per booking. Stored as a string in the source dataset.
     */
    @Field(Fields.MAXIMUM_NIGHTS)
    private String maximumNights;

    /**
     * Date of the first review, as a UTC instant.
     */
    @Field(Fields.FIRST_REVIEW)
    private Instant firstReview;

    /**
     * Date of the most recent review, as a UTC instant.
     */
    @Field(Fields.LAST_REVIEW)
    private Instant lastReview;

    /**
     * Listing imagery.
     */
    private Images images;

    /**
     * Host information.
     */
    private Host host;

    /**
     * Address, including the GeoJSON point used for proximity search.
     */
    private Address address;

    /**
     * Availability counters over rolling windows.
     */
    private Availability availability;

    /**
     * Guest review scores.
     */
    @Field(Fields.REVIEW_SCORES)
    private ReviewScores reviewScores;

    /**
     * Embedded guest reviews.
     *
     * <p>Reviews live inside the listing document in this dataset rather than in a separate
     * collection, so review reporting uses {@code $unwind} and {@code $slice} instead of
     * {@code $lookup}.
     */
    private List<Review> reviews;

    /**
     * Vector search similarity score.
     *
     * <p>Only populated on results returned by a {@code $vectorSearch} pipeline; it is not
     * a stored field on the listing document.
     */
    private Double score;

    /**
     * Nested class representing listing imagery.
     */
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    @NoArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    public static class Images {
        @Field("thumbnail_url")
        private String thumbnailUrl;

        @Field("medium_url")
        private String mediumUrl;

        @Field("picture_url")
        private String pictureUrl;

        @Field("xl_picture_url")
        private String xlPictureUrl;
    }

    /**
     * Nested class representing the host of a listing.
     */
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    @NoArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    public static class Host {
        @Field("host_id")
        private String hostId;

        @Field("host_url")
        private String hostUrl;

        @Field("host_name")
        private String hostName;

        @Field("host_location")
        private String hostLocation;

        @Field("host_about")
        private String hostAbout;

        @Field("host_thumbnail_url")
        private String hostThumbnailUrl;

        @Field("host_picture_url")
        private String hostPictureUrl;

        @Field("host_neighbourhood")
        private String hostNeighbourhood;

        @Field("host_listings_count")
        private Integer hostListingsCount;

        @Field("host_total_listings_count")
        private Integer hostTotalListingsCount;

        @Field("host_verifications")
        private List<String> hostVerifications;

        @Field("host_is_superhost")
        private Boolean hostIsSuperhost;

        @Field("host_has_profile_pic")
        private Boolean hostHasProfilePic;

        @Field("host_identity_verified")
        private Boolean hostIdentityVerified;
    }

    /**
     * Nested class representing a listing address.
     */
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    @NoArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    public static class Address {
        private String street;
        private String suburb;

        @Field("government_area")
        private String governmentArea;

        private String market;
        private String country;

        @Field("country_code")
        private String countryCode;

        /**
         * GeoJSON point for the listing.
         */
        private Location location;
    }

    /**
     * Nested class representing a GeoJSON point.
     *
     * <p>Modelled explicitly rather than using Spring Data's {@code GeoJsonPoint} so that the
     * dataset's extra {@code is_location_exact} flag survives a read/write round trip.
     * Coordinates are stored in GeoJSON order: <em>longitude first, then latitude</em>.
     */
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    @NoArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    public static class Location {
        /**
         * GeoJSON geometry type. Always "Point" for this dataset.
         */
        private String type;

        /**
         * Coordinate pair in [longitude, latitude] order.
         */
        private List<Double> coordinates;

        @Field("is_location_exact")
        private Boolean isLocationExact;
    }

    /**
     * Nested class representing availability counters.
     */
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    @NoArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    public static class Availability {
        @Field("availability_30")
        private Integer availability30;

        @Field("availability_60")
        private Integer availability60;

        @Field("availability_90")
        private Integer availability90;

        @Field("availability_365")
        private Integer availability365;
    }

    /**
     * Nested class representing guest review scores.
     */
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    @NoArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    public static class ReviewScores {
        @Field("review_scores_accuracy")
        private Integer accuracy;

        @Field("review_scores_cleanliness")
        private Integer cleanliness;

        @Field("review_scores_checkin")
        private Integer checkin;

        @Field("review_scores_communication")
        private Integer communication;

        @Field("review_scores_location")
        private Integer location;

        @Field("review_scores_value")
        private Integer value;

        /**
         * Overall rating on a 0-100 scale.
         */
        @Field("review_scores_rating")
        private Integer rating;
    }

    /**
     * Nested class representing a single embedded guest review.
     */
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    @NoArgsConstructor(access = AccessLevel.PROTECTED) // needed for Spring Data and MongoDB mapping
    public static class Review {
        @JsonProperty("_id")
        @Field("_id")
        private String id;

        /**
         * When the review was posted, as a UTC instant.
         */
        private Instant date;

        @Field("listing_id")
        private String listingId;

        @Field("reviewer_id")
        private String reviewerId;

        @Field("reviewer_name")
        private String reviewerName;

        /**
         * Free-text body of the review.
         */
        private String comments;
    }
}
