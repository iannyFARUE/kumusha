package com.kumusha.service;

import com.kumusha.model.Listing;
import com.kumusha.model.dto.AmenityStatisticsResult;
import com.kumusha.model.dto.BatchInsertResponse;
import com.kumusha.model.dto.BatchUpdateResponse;
import com.kumusha.model.dto.CreateListingRequest;
import com.kumusha.model.dto.DeleteResponse;
import com.kumusha.model.dto.EmbeddingBackfillResponse;
import com.kumusha.model.dto.ListingFacetsResult;
import com.kumusha.model.dto.ListingSearchQuery;
import com.kumusha.model.dto.ListingSearchRequest;
import com.kumusha.model.dto.ListingWithReviewsResult;
import com.kumusha.model.dto.NearbyListingResult;
import com.kumusha.model.dto.PropertyTypeStatisticsResult;
import com.kumusha.model.dto.UpdateListingRequest;
import com.kumusha.model.dto.VectorSearchResult;
import java.util.List;
import org.bson.Document;

/**
 * Service interface for listing business logic.
 */
public interface ListingService {

    List<Listing> getAllListings(ListingSearchQuery query);

    /**
     * Gets all distinct property type values from the listings collection.
     * Demonstrates the distinct() operation.
     *
     * @return List of unique property types, sorted alphabetically
     */
    List<String> getDistinctPropertyTypes();

    /**
     * Gets all distinct amenity values from the listings collection.
     *
     * <p>Demonstrates that distinct() flattens array fields: each listing holds an array of
     * amenities, and MongoDB returns the union of every element.
     *
     * @return List of unique amenities, sorted alphabetically
     */
    List<String> getDistinctAmenities();

    /**
     * Gets the values the filter UI needs in a single round trip: property types, room types,
     * markets and the observed price range.
     *
     * @return facet values for the listings collection
     */
    ListingFacetsResult getListingFacets();

    Listing getListingById(String id);

    Listing createListing(CreateListingRequest request);

    BatchInsertResponse createListingsBatch(List<CreateListingRequest> requests);

    Listing updateListing(String id, UpdateListingRequest request);

    BatchUpdateResponse updateListingsBatch(Document filter, Document update);

    DeleteResponse deleteListing(String id);

    DeleteResponse deleteListingsBatch(Document filter);

    Listing findAndDeleteListing(String id);

    // Aggregation endpoints for reporting

    /**
     * Aggregates the most recently reviewed listings along with their latest reviews.
     *
     * <p>Reviews are embedded in each listing document, so this uses {@code $unwind},
     * {@code $group} and {@code $slice} where a separate reviews collection would call for
     * {@code $lookup}.
     *
     * @param limit Maximum number of listings to return
     * @param listingId Optional listing id to restrict the report to one listing
     * @return List of listings with their most recent reviews
     */
    List<ListingWithReviewsResult> getListingsWithRecentReviews(Integer limit, String listingId);

    /**
     * Aggregates listings by property type with price and rating statistics.
     * Demonstrates {@code $group} with statistical accumulators.
     *
     * @param limit Maximum number of property types to return
     * @return List of per-property-type statistics
     */
    List<PropertyTypeStatisticsResult> getPropertyTypeStatistics(Integer limit);

    /**
     * Aggregates the most common amenities across all listings.
     * Demonstrates {@code $unwind} for array flattening followed by {@code $group}.
     *
     * @param limit Maximum number of amenities to return
     * @return List of amenities with their listing count and averages
     */
    List<AmenityStatisticsResult> getAmenityStatistics(Integer limit);

    // Geospatial endpoint

    /**
     * Finds listings near a geographic point, nearest first.
     *
     * <p>Demonstrates the {@code $geoNear} aggregation stage against the 2dsphere index on
     * {@code address.location}.
     *
     * @param longitude Longitude of the search origin (-180 to 180)
     * @param latitude Latitude of the search origin (-90 to 90)
     * @param maxDistanceMeters Maximum radius in metres
     * @param limit Maximum number of listings to return
     * @return Listings within the radius, ordered by distance ascending
     */
    List<NearbyListingResult> findNearbyListings(Double longitude, Double latitude,
                                                 Integer maxDistanceMeters, Integer limit);

    // MongoDB Search endpoints

    /**
     * Searches listings using MongoDB Search across multiple fields.
     * Demonstrates a Search index queried with compound operators.
     *
     * <p>Supports searching across:
     * <ul>
     * <li>summary, description, neighborhood - phrase operator for exact phrase matching</li>
     * <li>name, host, amenities - text operator with fuzzy matching</li>
     * </ul>
     *
     * @param searchRequest Search parameters including fields to search and compound operator
     * @return List of listings matching the search criteria
     */
    List<Listing> searchListings(ListingSearchRequest searchRequest);

    /**
     * Searches listings semantically using MongoDB Vector Search.
     *
     * <p>This method:
     * <ul>
     * <li>Generates an embedding for the search query using Voyage AI</li>
     * <li>Runs {@code $vectorSearch} against the stored description embeddings</li>
     * <li>Returns listings with their similarity scores</li>
     * </ul>
     *
     * @param query Natural-language description of the stay to find
     * @param limit Maximum number of results to return (default: 10, max: 50)
     * @return List of vector search results with similarity scores
     */
    List<VectorSearchResult> vectorSearchListings(String query, Integer limit);

    /**
     * Finds listings similar to a given listing using its stored description embedding.
     *
     * @param listingId Id of the listing to find neighbours for
     * @param limit Maximum number of similar listings to return (default: 10, max: 50)
     * @return List of similar listings
     */
    List<Listing> findSimilarListings(String listingId, Integer limit);

    /**
     * Generates and stores description embeddings for listings that do not have one yet.
     *
     * <p>The stock {@code sample_airbnb} dataset ships without embeddings, so this step is what
     * makes vector search possible. It is idempotent: listings that already carry an embedding
     * are skipped, so the endpoint can be called repeatedly to work through the collection.
     *
     * @param limit Maximum number of listings to embed in this run (default: 50, max: 200)
     * @return Counts describing what the run did and how much work remains
     */
    EmbeddingBackfillResponse backfillDescriptionEmbeddings(Integer limit);
}
