package com.kumusha.controller;

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
import com.kumusha.model.dto.SearchListingsResponse;
import com.kumusha.model.dto.UpdateListingRequest;
import com.kumusha.model.dto.VectorSearchResult;
import com.kumusha.model.response.SuccessResponse;
import com.kumusha.service.ListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for listing-related endpoints.
 *
 * <p>This controller handles all HTTP requests for listing operations including:
 * <pre>
 * - GET    /api/listings                                    - List listings with filtering, sorting and pagination
 * - GET    /api/listings/property-types                     - Distinct property types
 * - GET    /api/listings/amenities                          - Distinct amenities
 * - GET    /api/listings/facets                             - Filter values and price range in one call
 * - GET    /api/listings/{id}                               - Get a single listing by id
 * - POST   /api/listings                                    - Create a listing
 * - POST   /api/listings/batch                              - Create multiple listings
 * - PATCH  /api/listings/{id}                               - Update a listing
 * - PATCH  /api/listings                                    - Update multiple listings
 * - DELETE /api/listings/{id}                               - Delete a listing
 * - DELETE /api/listings                                    - Delete multiple listings
 * - DELETE /api/listings/{id}/find-and-delete               - Find and delete a listing atomically
 * - GET    /api/listings/aggregations/reportingByReviews    - Most recently reviewed listings ($unwind/$group/$slice)
 * - GET    /api/listings/aggregations/reportingByPropertyType - Price and rating statistics per property type ($group)
 * - GET    /api/listings/aggregations/reportingByAmenities  - Most common amenities ($unwind + $group)
 * - GET    /api/listings/nearby                             - Proximity search ($geoNear)
 * - GET    /api/listings/search                             - Full-text search using MongoDB Search
 * - GET    /api/listings/vector-search                      - Semantic search using MongoDB Vector Search
 * - GET    /api/listings/find-similar-listings              - Nearest neighbours of a listing
 * - POST   /api/listings/embeddings/backfill                - Generate the embeddings vector search needs
 * </pre>
 */
@RestController
@RequestMapping("/api/listings")
@Tag(name = "Listings", description = "Listing endpoints for CRUD operations, reporting, proximity search and semantic search")
public class ListingControllerImpl {

    private final ListingService listingService;

    public ListingControllerImpl(ListingService listingService) {
        this.listingService = listingService;
    }

    @Operation(
        summary = "Get all listings with optional filtering, sorting, and pagination",
        description = "Retrieve a list of listings with optional filtering by text search, property type, room type, " +
                     "market, country, amenity, price range, capacity and review score. Supports sorting and " +
                     "pagination. Text search (q parameter) uses the MongoDB text index across name, summary and " +
                     "description."
    )
    @GetMapping
    public ResponseEntity<SuccessResponse<List<Listing>>> getAllListings(
            @Parameter(description = "Text search query (searches name, summary, description)")
            @RequestParam(required = false) String q,
            @Parameter(description = "Filter by property type, e.g. 'Apartment'")
            @RequestParam(required = false) String propertyType,
            @Parameter(description = "Filter by room type, e.g. 'Entire home/apt'")
            @RequestParam(required = false) String roomType,
            @Parameter(description = "Filter by market / city, e.g. 'Porto'")
            @RequestParam(required = false) String market,
            @Parameter(description = "Filter by country")
            @RequestParam(required = false) String country,
            @Parameter(description = "Filter to listings offering this amenity, e.g. 'Wifi'")
            @RequestParam(required = false) String amenity,
            @Parameter(description = "Minimum nightly price (inclusive)")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum nightly price (inclusive)")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Minimum number of bedrooms")
            @RequestParam(required = false) Integer minBedrooms,
            @Parameter(description = "Minimum number of guests accommodated")
            @RequestParam(required = false) Integer minAccommodates,
            @Parameter(description = "Minimum overall review score (0-100 scale)")
            @RequestParam(required = false) Integer minRating,
            @Parameter(description = "Only return listings hosted by a superhost")
            @RequestParam(required = false) Boolean superhostOnly,
            @Parameter(description = "Number of results to return (default: 20, max: 100)")
            @RequestParam(defaultValue = "20") Integer limit,
            @Parameter(description = "Number of results to skip for pagination (default: 0)")
            @RequestParam(defaultValue = "0") Integer skip,
            @Parameter(description = "Field to sort by (default: name)")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sort order: 'asc' or 'desc' (default: asc)")
            @RequestParam(defaultValue = "asc") String sortOrder) {

        ListingSearchQuery query = ListingSearchQuery.builder()
                .q(q)
                .propertyType(propertyType)
                .roomType(roomType)
                .market(market)
                .country(country)
                .amenity(amenity)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minBedrooms(minBedrooms)
                .minAccommodates(minAccommodates)
                .minRating(minRating)
                .superhostOnly(superhostOnly)
                .limit(limit)
                .skip(skip)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .build();

        List<Listing> listings = listingService.getAllListings(query);

        SuccessResponse<List<Listing>> response = SuccessResponse.<List<Listing>>builder()
                .message("Found " + listings.size() + " listings")
                .data(listings)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Get all distinct property types",
        description = "Retrieve a list of all unique property type values from the listings collection. " +
                     "Demonstrates the distinct() operation. Returns property types sorted alphabetically."
    )
    @GetMapping("/property-types")
    public ResponseEntity<SuccessResponse<List<String>>> getDistinctPropertyTypes() {
        List<String> propertyTypes = listingService.getDistinctPropertyTypes();

        SuccessResponse<List<String>> response = SuccessResponse.<List<String>>builder()
                .message("Found " + propertyTypes.size() + " distinct property types")
                .data(propertyTypes)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Get all distinct amenities",
        description = "Retrieve a list of all unique amenities across the listings collection. " +
                     "Demonstrates that distinct() flattens array fields, returning individual amenities " +
                     "rather than arrays."
    )
    @GetMapping("/amenities")
    public ResponseEntity<SuccessResponse<List<String>>> getDistinctAmenities() {
        List<String> amenities = listingService.getDistinctAmenities();

        SuccessResponse<List<String>> response = SuccessResponse.<List<String>>builder()
                .message("Found " + amenities.size() + " distinct amenities")
                .data(amenities)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Get filter facets",
        description = "Retrieve property types, room types, markets and the observed price range in a single " +
                     "request so the filter UI does not need several round trips on mount."
    )
    @GetMapping("/facets")
    public ResponseEntity<SuccessResponse<ListingFacetsResult>> getListingFacets() {
        ListingFacetsResult facets = listingService.getListingFacets();

        SuccessResponse<ListingFacetsResult> response = SuccessResponse.<ListingFacetsResult>builder()
                .message("Filter facets retrieved successfully")
                .data(facets)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Get a single listing by id",
        description = "Retrieve a single listing by its _id. Ids in the sample_airbnb dataset are strings, " +
                     "not ObjectIds."
    )
    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<Listing>> getListingById(
            @Parameter(description = "Listing id", required = true)
            @PathVariable String id) {
        Listing listing = listingService.getListingById(id);

        SuccessResponse<Listing> response = SuccessResponse.<Listing>builder()
                .message("Listing retrieved successfully")
                .data(listing)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Create a new listing",
        description = "Create a single new listing document. Only the name field is required; all other fields " +
                     "are optional. Supplying both longitude and latitude builds the GeoJSON point used by " +
                     "proximity search."
    )
    @PostMapping
    public ResponseEntity<SuccessResponse<Listing>> createListing(
            @Parameter(description = "Listing data to create", required = true)
            @Valid @RequestBody CreateListingRequest request) {
        Listing listing = listingService.createListing(request);

        SuccessResponse<Listing> response = SuccessResponse.<Listing>builder()
                .message("Listing '" + request.name() + "' created successfully")
                .data(listing)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
        summary = "Create multiple listings in batch",
        description = "Create multiple listing documents in a single operation using insertMany."
    )
    @PostMapping("/batch")
    public ResponseEntity<SuccessResponse<BatchInsertResponse>> createListingsBatch(
            @Parameter(description = "List of listings to create", required = true)
            @RequestBody List<CreateListingRequest> requests) {
        BatchInsertResponse result = listingService.createListingsBatch(requests);

        SuccessResponse<BatchInsertResponse> response = SuccessResponse.<BatchInsertResponse>builder()
                .message("Successfully created " + result.insertedCount() + " listings")
                .data(result)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
        summary = "Update a listing by id",
        description = "Update a single listing document by its id using updateOne with the $set operator. " +
                     "Only the fields present in the request body are modified."
    )
    @PatchMapping("/{id}")
    public ResponseEntity<SuccessResponse<Listing>> updateListing(
            @Parameter(description = "Listing id to update", required = true)
            @PathVariable String id,
            @Parameter(description = "Updated listing data (only provided fields will be updated)", required = true)
            @RequestBody UpdateListingRequest request) {
        Listing listing = listingService.updateListing(id, request);

        SuccessResponse<Listing> response = SuccessResponse.<Listing>builder()
                .message("Listing updated successfully")
                .data(listing)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Update multiple listings in batch",
        description = "Update every listing matching the given filter using updateMany. The request body must " +
                     "contain 'filter' and 'update' objects. Both accept the camelCase field names used " +
                     "elsewhere in the API and translate them to their stored paths."
    )
    @SuppressWarnings("unchecked")
    @PatchMapping
    public ResponseEntity<SuccessResponse<BatchUpdateResponse>> updateListingsBatch(
            @Parameter(description = "Request body with 'filter' and 'update' objects", required = true)
            @RequestBody Map<String, Object> body) {
        Document filter = new Document((Map<String, Object>) body.get("filter"));
        Document update = new Document((Map<String, Object>) body.get("update"));

        BatchUpdateResponse result = listingService.updateListingsBatch(filter, update);

        SuccessResponse<BatchUpdateResponse> response = SuccessResponse.<BatchUpdateResponse>builder()
                .message("Update operation completed. Matched " + result.matchedCount() +
                        " documents, modified " + result.modifiedCount() + " documents.")
                .data(result)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Find and delete a listing atomically",
        description = "Find and delete a listing in a single atomic operation using findOneAndDelete. " +
                     "Returns the deleted listing document."
    )
    @DeleteMapping("/{id}/find-and-delete")
    public ResponseEntity<SuccessResponse<Listing>> findAndDeleteListing(
            @Parameter(description = "Listing id to find and delete", required = true)
            @PathVariable String id) {
        Listing listing = listingService.findAndDeleteListing(id);

        SuccessResponse<Listing> response = SuccessResponse.<Listing>builder()
                .message("Listing found and deleted successfully")
                .data(listing)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Delete a listing by id",
        description = "Delete a single listing document by its id using deleteOne."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse<DeleteResponse>> deleteListing(
            @Parameter(description = "Listing id to delete", required = true)
            @PathVariable String id) {
        DeleteResponse result = listingService.deleteListing(id);

        SuccessResponse<DeleteResponse> response = SuccessResponse.<DeleteResponse>builder()
                .message("Listing deleted successfully")
                .data(result)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Delete multiple listings in batch",
        description = "Delete every listing matching the given filter using deleteMany. The request body must " +
                     "contain a non-empty 'filter' object; an empty filter is rejected to prevent accidentally " +
                     "emptying the collection."
    )
    @SuppressWarnings("unchecked")
    @DeleteMapping
    public ResponseEntity<SuccessResponse<DeleteResponse>> deleteListingsBatch(
            @Parameter(description = "Request body with a 'filter' object", required = true)
            @RequestBody Map<String, Object> body) {
        Document filter = new Document((Map<String, Object>) body.get("filter"));

        DeleteResponse result = listingService.deleteListingsBatch(filter);

        SuccessResponse<DeleteResponse> response = SuccessResponse.<DeleteResponse>builder()
                .message("Delete operation completed. Removed " + result.deletedCount() + " documents.")
                .data(result)
                .build();

        return ResponseEntity.ok(response);
    }

    // Aggregation endpoints for reporting

    @Operation(
        summary = "Aggregate the most recently reviewed listings",
        description = "Returns listings ordered by review recency together with their latest reviews. " +
                     "Reviews are embedded in each listing document in this dataset, so the pipeline uses " +
                     "$unwind, $group and $slice where a separate reviews collection would use $lookup."
    )
    @GetMapping("/aggregations/reportingByReviews")
    public ResponseEntity<SuccessResponse<List<ListingWithReviewsResult>>> getListingsWithRecentReviews(
            @Parameter(description = "Maximum number of listings to return (default: 10, max: 50)")
            @RequestParam(defaultValue = "10") Integer limit,
            @Parameter(description = "Optional listing id to restrict the report to a single listing")
            @RequestParam(required = false) String listingId) {

        List<ListingWithReviewsResult> results = listingService.getListingsWithRecentReviews(limit, listingId);

        int totalReviews = results.stream()
                .mapToInt(result -> result.totalReviews() != null ? result.totalReviews() : 0)
                .sum();

        String message = listingId != null
                ? String.format("Found %d reviews for the listing", totalReviews)
                : String.format("Found %d reviews across %d listing%s",
                        totalReviews, results.size(), results.size() != 1 ? "s" : "");

        SuccessResponse<List<ListingWithReviewsResult>> response =
                SuccessResponse.<List<ListingWithReviewsResult>>builder()
                        .message(message)
                        .data(results)
                        .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Aggregate listings by property type with statistics",
        description = "Groups listings by property type and computes listing count, average, highest and lowest " +
                     "nightly price, average review score and average capacity. Demonstrates MongoDB's $group " +
                     "statistical accumulators."
    )
    @GetMapping("/aggregations/reportingByPropertyType")
    public ResponseEntity<SuccessResponse<List<PropertyTypeStatisticsResult>>> getPropertyTypeStatistics(
            @Parameter(description = "Maximum number of property types to return (default: 20, max: 100)")
            @RequestParam(defaultValue = "20") Integer limit) {

        List<PropertyTypeStatisticsResult> results = listingService.getPropertyTypeStatistics(limit);

        SuccessResponse<List<PropertyTypeStatisticsResult>> response =
                SuccessResponse.<List<PropertyTypeStatisticsResult>>builder()
                        .message(String.format("Aggregated statistics for %d property types", results.size()))
                        .data(results)
                        .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Aggregate the most common amenities",
        description = "Flattens the amenities array with $unwind and groups by individual amenity to show how " +
                     "common each one is and what listings offering it cost on average."
    )
    @GetMapping("/aggregations/reportingByAmenities")
    public ResponseEntity<SuccessResponse<List<AmenityStatisticsResult>>> getAmenityStatistics(
            @Parameter(description = "Maximum number of amenities to return (default: 20, max: 100)")
            @RequestParam(defaultValue = "20") Integer limit) {

        List<AmenityStatisticsResult> results = listingService.getAmenityStatistics(limit);

        SuccessResponse<List<AmenityStatisticsResult>> response =
                SuccessResponse.<List<AmenityStatisticsResult>>builder()
                        .message(String.format("Found the %d most common amenities", results.size()))
                        .data(results)
                        .build();

        return ResponseEntity.ok(response);
    }

    // Geospatial endpoint

    @Operation(
        summary = "Find listings near a point",
        description = "Returns listings within a radius of a coordinate, nearest first, using the $geoNear " +
                     "aggregation stage against the 2dsphere index on address.location. Each result includes " +
                     "its distance from the query point in metres."
    )
    @GetMapping("/nearby")
    public ResponseEntity<SuccessResponse<List<NearbyListingResult>>> findNearbyListings(
            @Parameter(description = "Longitude of the search origin (-180 to 180)", required = true)
            @RequestParam Double longitude,
            @Parameter(description = "Latitude of the search origin (-90 to 90)", required = true)
            @RequestParam Double latitude,
            @Parameter(description = "Search radius in metres (default: 5000, max: 200000)")
            @RequestParam(defaultValue = "5000") Integer maxDistanceMeters,
            @Parameter(description = "Maximum number of listings to return (default: 20, max: 100)")
            @RequestParam(defaultValue = "20") Integer limit) {

        List<NearbyListingResult> results =
                listingService.findNearbyListings(longitude, latitude, maxDistanceMeters, limit);

        SuccessResponse<List<NearbyListingResult>> response =
                SuccessResponse.<List<NearbyListingResult>>builder()
                        .message(String.format("Found %d listings within %d metres", results.size(), maxDistanceMeters))
                        .data(results)
                        .build();

        return ResponseEntity.ok(response);
    }

    // MongoDB Search endpoints

    @Operation(
        summary = "Search listings using MongoDB Search",
        description = "Search listings across multiple fields (summary, description, neighborhood, name, host, " +
                     "amenities). You can combine several fields in one query and control how they are combined " +
                     "with the searchOperator parameter. At least one search field must be provided. Summary, " +
                     "description and neighborhood use the phrase operator for exact phrase matching, while name, " +
                     "host and amenities use the text operator with fuzzy matching."
    )
    @GetMapping("/search")
    public ResponseEntity<SuccessResponse<SearchListingsResponse>> searchListings(
            @Parameter(description = "Text to search in the summary field (phrase matching)")
            @RequestParam(required = false) String summary,
            @Parameter(description = "Text to search in the description field (phrase matching)")
            @RequestParam(required = false) String description,
            @Parameter(description = "Text to search in the neighborhood overview (phrase matching)")
            @RequestParam(required = false) String neighborhood,
            @Parameter(description = "Text to search in the listing name (fuzzy matching)")
            @RequestParam(required = false) String name,
            @Parameter(description = "Text to search in the host name (fuzzy matching)")
            @RequestParam(required = false) String host,
            @Parameter(description = "Text to search in the amenities (fuzzy matching)")
            @RequestParam(required = false) String amenities,
            @Parameter(description = "Maximum number of listings to return (default: 20, max: 100)")
            @RequestParam(defaultValue = "20") Integer limit,
            @Parameter(description = "Number of results to skip for pagination (default: 0)")
            @RequestParam(defaultValue = "0") Integer skip,
            @Parameter(description = "Compound operator: must, should, mustNot, or filter (default: must)")
            @RequestParam(defaultValue = "must") String searchOperator) {

        ListingSearchRequest searchRequest = ListingSearchRequest.builder()
                .summary(summary)
                .description(description)
                .neighborhood(neighborhood)
                .name(name)
                .host(host)
                .amenities(amenities)
                .limit(limit)
                .skip(skip)
                .searchOperator(searchOperator)
                .build();

        List<Listing> listings = listingService.searchListings(searchRequest);

        SearchListingsResponse searchResponse = SearchListingsResponse.builder()
                .listings(listings)
                .totalCount(listings.size())
                .build();

        SuccessResponse<SearchListingsResponse> response = SuccessResponse.<SearchListingsResponse>builder()
                .message(String.format("Found %d listings matching the search criteria", listings.size()))
                .data(searchResponse)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Search listings semantically with vector search",
        description = "Finds listings whose descriptions are semantically closest to a natural-language query, " +
                     "for example 'quiet cabin near the beach with a fireplace'. The query is embedded with " +
                     "Voyage AI and compared against stored description embeddings. Requires VOYAGE_API_KEY and a " +
                     "prior run of the embedding backfill endpoint."
    )
    @GetMapping("/vector-search")
    public ResponseEntity<SuccessResponse<List<VectorSearchResult>>> vectorSearchListings(
            @Parameter(description = "Natural-language description of the stay to find", required = true)
            @RequestParam String q,
            @Parameter(description = "Maximum number of results to return (default: 10, max: 50)")
            @RequestParam(defaultValue = "10") Integer limit) {

        List<VectorSearchResult> results = listingService.vectorSearchListings(q, limit);

        SuccessResponse<List<VectorSearchResult>> response = SuccessResponse.<List<VectorSearchResult>>builder()
                .message(String.format("Found %d listings for query: '%s'", results.size(), q))
                .data(results)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Find similar listings using vector search",
        description = "Finds the nearest neighbours of a listing using its stored description embedding. " +
                     "The source listing itself is excluded from the results."
    )
    @GetMapping("/find-similar-listings")
    public ResponseEntity<SuccessResponse<List<Listing>>> findSimilarListings(
            @Parameter(description = "Id of the listing to find neighbours for", required = true)
            @RequestParam String listingId,
            @Parameter(description = "Maximum number of similar listings to return (default: 10, max: 50)")
            @RequestParam(defaultValue = "10") Integer limit) {

        List<Listing> listings = listingService.findSimilarListings(listingId, limit);

        SuccessResponse<List<Listing>> response = SuccessResponse.<List<Listing>>builder()
                .message(String.format("Found %d similar listings", listings.size()))
                .data(listings)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Generate description embeddings",
        description = "The sample_airbnb dataset ships without embeddings, so vector search has nothing to " +
                     "compare against until this endpoint has run. It embeds listings that do not yet have a " +
                     "vector and is safe to call repeatedly: already-embedded listings are skipped, and the " +
                     "response reports how many listings remain."
    )
    @PostMapping("/embeddings/backfill")
    public ResponseEntity<SuccessResponse<EmbeddingBackfillResponse>> backfillEmbeddings(
            @Parameter(description = "Maximum number of listings to embed in this run (default: 50, max: 200)")
            @RequestParam(defaultValue = "50") Integer limit) {

        EmbeddingBackfillResponse result = listingService.backfillDescriptionEmbeddings(limit);

        SuccessResponse<EmbeddingBackfillResponse> response =
                SuccessResponse.<EmbeddingBackfillResponse>builder()
                        .message(String.format("Embedded %d listings, %d still pending",
                                result.embeddedCount(), result.remainingCount()))
                        .data(result)
                        .build();

        return ResponseEntity.ok(response);
    }
}
