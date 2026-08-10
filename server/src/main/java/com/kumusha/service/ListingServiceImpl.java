package com.kumusha.service;

import static java.util.Map.entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kumusha.exception.DatabaseOperationException;
import com.kumusha.exception.ResourceNotFoundException;
import com.kumusha.exception.ServiceUnavailableException;
import com.kumusha.exception.ValidationException;
import com.kumusha.exception.VoyageAPIException;
import com.kumusha.exception.VoyageAuthException;
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
import com.kumusha.repository.ListingRepository;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Service layer for listing business logic using Spring Data MongoDB.
 *
 * <p>This service handles:
 * <pre>
 * - Business logic and validation
 * - Query construction using the Spring Data MongoDB Query and Aggregation APIs
 * - Data transformation between DTOs and entities
 * - Error handling and exception throwing
 * </pre>
 * Uses both:
 * <pre>
 * - ListingRepository (Spring Data) for simple CRUD operations
 * - MongoTemplate for aggregations, batch operations and raw driver pipelines
 * </pre>
 *
 * <p>Two things differ from a typical MongoDB sample application because of the shape of the
 * {@code sample_airbnb} dataset:
 * <ol>
 *   <li>{@code _id} values are strings, so there is no ObjectId parsing or validation.</li>
 *   <li>The dataset ships without embeddings, so vector search depends on
 *       {@link #backfillDescriptionEmbeddings(Integer)} having been run first.</li>
 * </ol>
 */
@Service
public class ListingServiceImpl implements ListingService {

    private static final String COLLECTION = "listingsAndReviews";
    private static final String SEARCH_INDEX = "listingSearchIndex";
    private static final String VECTOR_INDEX = "vector_index";

    /**
     * Voyage AI caps the number of inputs per embedding request; batching keeps the backfill
     * to a handful of HTTP round trips instead of one per listing.
     */
    private static final int EMBEDDING_BATCH_SIZE = 32;

    /**
     * Maps the camelCase property names used by the JSON API onto MongoDB field paths.
     *
     * <p>The API speaks camelCase because Jackson serialises Java property names, while the
     * documents themselves use snake_case with nested subdocuments. Every write path routes
     * client-supplied keys through this map so callers never have to know the storage layout.
     * Keys that are not listed are passed through unchanged, which lets advanced callers send
     * raw MongoDB paths.
     */
    private static final Map<String, String> FIELD_PATHS = Map.ofEntries(
            entry("id", Listing.Fields.ID),
            entry("_id", Listing.Fields.ID),
            entry("name", Listing.Fields.NAME),
            entry("listingUrl", Listing.Fields.LISTING_URL),
            entry("summary", Listing.Fields.SUMMARY),
            entry("space", Listing.Fields.SPACE),
            entry("description", Listing.Fields.DESCRIPTION),
            entry("neighborhoodOverview", Listing.Fields.NEIGHBORHOOD_OVERVIEW),
            entry("notes", Listing.Fields.NOTES),
            entry("transit", Listing.Fields.TRANSIT),
            entry("houseRules", Listing.Fields.HOUSE_RULES),
            entry("propertyType", Listing.Fields.PROPERTY_TYPE),
            entry("roomType", Listing.Fields.ROOM_TYPE),
            entry("bedType", Listing.Fields.BED_TYPE),
            entry("cancellationPolicy", Listing.Fields.CANCELLATION_POLICY),
            entry("accommodates", Listing.Fields.ACCOMMODATES),
            entry("bedrooms", Listing.Fields.BEDROOMS),
            entry("beds", Listing.Fields.BEDS),
            entry("bathrooms", Listing.Fields.BATHROOMS),
            entry("numberOfReviews", Listing.Fields.NUMBER_OF_REVIEWS),
            entry("amenities", Listing.Fields.AMENITIES),
            entry("price", Listing.Fields.PRICE),
            entry("cleaningFee", Listing.Fields.CLEANING_FEE),
            entry("securityDeposit", Listing.Fields.SECURITY_DEPOSIT),
            entry("minimumNights", Listing.Fields.MINIMUM_NIGHTS),
            entry("maximumNights", Listing.Fields.MAXIMUM_NIGHTS),
            entry("pictureUrl", Listing.Fields.IMAGES_PICTURE_URL),
            entry("hostName", Listing.Fields.HOST_NAME),
            entry("superhost", Listing.Fields.HOST_IS_SUPERHOST),
            entry("market", Listing.Fields.ADDRESS_MARKET),
            entry("country", Listing.Fields.ADDRESS_COUNTRY),
            entry("suburb", Listing.Fields.ADDRESS_SUBURB),
            entry("reviewScore", Listing.Fields.REVIEW_SCORES_RATING)
    );

    private final ListingRepository listingRepository;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    @Value("${voyage.api.key:#{null}}")
    private String voyageApiKey;

    @Value("${kumusha.embedding.field}")
    private String embeddingField;

    @Value("${kumusha.embedding.dimensions}")
    private int embeddingDimensions;

    @Value("${kumusha.embedding.model}")
    private String embeddingModel;

    public ListingServiceImpl(ListingRepository listingRepository, MongoTemplate mongoTemplate,
                              ObjectMapper objectMapper) {
        this.listingRepository = listingRepository;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    // ==================== READ ====================

    @Override
    public List<Listing> getAllListings(ListingSearchQuery query) {
        Query mongoQuery = buildQuery(query);

        int limit = Math.clamp(query.limit() != null ? query.limit() : 20, 1, 100);
        int skip = Math.max(query.skip() != null ? query.skip() : 0, 0);

        mongoQuery.skip(skip).limit(limit);
        mongoQuery.with(buildSort(query.sortBy(), query.sortOrder()));

        // Never ship the embedding vector to the client: it is thousands of doubles per document
        mongoQuery.fields().exclude(embeddingField);

        return mongoTemplate.find(mongoQuery, Listing.class);
    }

    @Override
    public List<String> getDistinctPropertyTypes() {
        return distinctSortedStrings(Listing.Fields.PROPERTY_TYPE);
    }

    @Override
    public List<String> getDistinctAmenities() {
        // MongoDB flattens array fields for distinct(), so this returns individual amenities
        // rather than arrays of them
        return distinctSortedStrings(Listing.Fields.AMENITIES);
    }

    @Override
    public ListingFacetsResult getListingFacets() {
        List<String> propertyTypes = distinctSortedStrings(Listing.Fields.PROPERTY_TYPE);
        List<String> roomTypes = distinctSortedStrings(Listing.Fields.ROOM_TYPE);
        List<String> markets = distinctSortedStrings(Listing.Fields.ADDRESS_MARKET);

        Double minPrice = null;
        Double maxPrice = null;

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where(Listing.Fields.PRICE).exists(true).ne(null)),
                Aggregation.project().and(toDouble(Listing.Fields.PRICE)).as("priceValue"),
                Aggregation.group()
                        .min("priceValue").as("minPrice")
                        .max("priceValue").as("maxPrice")
        );

        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);
        Document priceRange = results.getUniqueMappedResult();

        if (priceRange != null) {
            minPrice = toDouble(priceRange.get("minPrice"));
            maxPrice = toDouble(priceRange.get("maxPrice"));
        }

        return ListingFacetsResult.builder()
                .propertyTypes(propertyTypes)
                .roomTypes(roomTypes)
                .markets(markets)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .build();
    }

    @Override
    public Listing getListingById(String id) {
        String listingId = requireListingId(id);

        return listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
    }

    // ==================== WRITE ====================

    @Override
    public Listing createListing(CreateListingRequest request) {
        if (request.name() == null || request.name().trim().isEmpty()) {
            throw new ValidationException("Name is required");
        }

        Listing listing = toListing(request);

        // Spring Data MongoDB's save() method inserts or updates
        return listingRepository.save(listing);
    }

    @Override
    public BatchInsertResponse createListingsBatch(List<CreateListingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ValidationException("Request body must be a non-empty array of listing objects");
        }

        for (int i = 0; i < requests.size(); i++) {
            CreateListingRequest request = requests.get(i);
            if (request.name() == null || request.name().trim().isEmpty()) {
                throw new ValidationException("Listing at index " + i + ": Name is required");
            }
        }

        List<Listing> listings = requests.stream()
                .map(this::toListing)
                .toList();

        // Spring Data MongoDB's saveAll() method for batch insert
        List<Listing> savedListings = listingRepository.saveAll(listings);

        List<String> insertedIds = savedListings.stream()
                .map(Listing::getId)
                .collect(Collectors.toList());

        return new BatchInsertResponse(savedListings.size(), insertedIds);
    }

    @Override
    public Listing updateListing(String id, UpdateListingRequest request) {
        String listingId = requireListingId(id);

        if (request == null) {
            throw new ValidationException("No update data provided");
        }

        Map<String, Object> updates = toUpdateMap(request);
        if (updates.isEmpty()) {
            throw new ValidationException("No update data provided");
        }

        Update update = new Update();
        updates.forEach(update::set);

        Query query = new Query(Criteria.where(Listing.Fields.ID).is(listingId));
        UpdateResult result = mongoTemplate.updateFirst(query, update, Listing.class);

        if (result.getMatchedCount() == 0) {
            throw new ResourceNotFoundException("Listing not found");
        }

        return listingRepository.findById(listingId)
                .orElseThrow(() -> new DatabaseOperationException("Failed to retrieve updated listing"));
    }

    @Override
    public BatchUpdateResponse updateListingsBatch(Document filter, Document update) {
        if (filter == null || update == null) {
            throw new ValidationException("Both filter and update objects are required");
        }

        if (update.isEmpty()) {
            throw new ValidationException("Update object cannot be empty");
        }

        Query query = buildQueryFromFilter(filter);

        Update mongoUpdate = new Update();
        update.forEach((key, value) -> mongoUpdate.set(toMongoPath(key), value));

        UpdateResult result = mongoTemplate.updateMulti(query, mongoUpdate, Listing.class);

        return new BatchUpdateResponse(result.getMatchedCount(), result.getModifiedCount());
    }

    @Override
    public DeleteResponse deleteListing(String id) {
        String listingId = requireListingId(id);

        if (!listingRepository.existsById(listingId)) {
            throw new ResourceNotFoundException("Listing not found");
        }

        listingRepository.deleteById(listingId);

        return new DeleteResponse(1L);
    }

    @Override
    public DeleteResponse deleteListingsBatch(Document filter) {
        if (filter == null || filter.isEmpty()) {
            throw new ValidationException(
                "Filter object is required and cannot be empty. This prevents accidental deletion of all documents.");
        }

        Query query = buildQueryFromFilter(filter);
        DeleteResult result = mongoTemplate.remove(query, Listing.class);

        return new DeleteResponse(result.getDeletedCount());
    }

    @Override
    public Listing findAndDeleteListing(String id) {
        String listingId = requireListingId(id);

        Query query = new Query(Criteria.where(Listing.Fields.ID).is(listingId));
        Listing listing = mongoTemplate.findAndRemove(query, Listing.class);

        if (listing == null) {
            throw new ResourceNotFoundException("Listing not found");
        }

        return listing;
    }

    // ==================== QUERY BUILDING ====================

    /**
     * Builds a Spring Data MongoDB Query from the search parameters.
     */
    private Query buildQuery(ListingSearchQuery query) {
        Query mongoQuery = new Query();

        // Text search across name, summary and description (uses the text index)
        if (isPresent(query.q())) {
            mongoQuery.addCriteria(TextCriteria.forDefaultLanguage().matching(query.q()));
        }

        addExactIgnoreCase(mongoQuery, Listing.Fields.PROPERTY_TYPE, query.propertyType());
        addExactIgnoreCase(mongoQuery, Listing.Fields.ROOM_TYPE, query.roomType());
        addExactIgnoreCase(mongoQuery, Listing.Fields.ADDRESS_MARKET, query.market());
        addExactIgnoreCase(mongoQuery, Listing.Fields.ADDRESS_COUNTRY, query.country());
        // Matching an array field against a scalar matches listings where any element matches
        addExactIgnoreCase(mongoQuery, Listing.Fields.AMENITIES, query.amenity());

        // Price range - both bounds must be chained onto a single Criteria for the same field
        if (query.minPrice() != null || query.maxPrice() != null) {
            Criteria priceCriteria = Criteria.where(Listing.Fields.PRICE);
            if (query.minPrice() != null) {
                priceCriteria = priceCriteria.gte(query.minPrice());
            }
            if (query.maxPrice() != null) {
                priceCriteria = priceCriteria.lte(query.maxPrice());
            }
            mongoQuery.addCriteria(priceCriteria);
        }

        if (query.minBedrooms() != null) {
            mongoQuery.addCriteria(Criteria.where(Listing.Fields.BEDROOMS).gte(query.minBedrooms()));
        }

        if (query.minAccommodates() != null) {
            mongoQuery.addCriteria(Criteria.where(Listing.Fields.ACCOMMODATES).gte(query.minAccommodates()));
        }

        if (query.minRating() != null) {
            mongoQuery.addCriteria(Criteria.where(Listing.Fields.REVIEW_SCORES_RATING).gte(query.minRating()));
        }

        if (Boolean.TRUE.equals(query.superhostOnly())) {
            mongoQuery.addCriteria(Criteria.where(Listing.Fields.HOST_IS_SUPERHOST).is(true));
        }

        return mongoQuery;
    }

    /**
     * Adds a case-insensitive exact match on the given field, escaping the user input so that
     * regex metacharacters in values such as "Entire home/apt" are treated literally.
     */
    private void addExactIgnoreCase(Query query, String field, String value) {
        if (!isPresent(value)) {
            return;
        }
        Pattern pattern = Pattern.compile("^" + Pattern.quote(value.trim()) + "$", Pattern.CASE_INSENSITIVE);
        query.addCriteria(Criteria.where(field).regex(pattern));
    }

    /**
     * Builds a Spring Data Sort object from sort parameters.
     */
    private Sort buildSort(String sortBy, String sortOrder) {
        String field = isPresent(sortBy) ? toMongoPath(sortBy.trim()) : Listing.Fields.NAME;
        Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }

    /**
     * Converts a client-supplied filter document into a Spring Data Query.
     */
    private Query buildQueryFromFilter(Document filter) {
        Query query = new Query();
        filter.forEach((key, value) -> query.addCriteria(buildCriteriaFromValue(toMongoPath(key), value)));
        return query;
    }

    /**
     * Builds a Spring Data Criteria from a filter key-value pair.
     * Handles MongoDB query operators such as $in, $gt and $lt.
     *
     * @param key The MongoDB field path (already translated)
     * @param value The filter value (a plain value, or a document of operators)
     * @return Criteria object for the query
     */
    @SuppressWarnings("unchecked")
    private Criteria buildCriteriaFromValue(String key, Object value) {
        Criteria criteria = Criteria.where(key);

        // If the value is a document, it may contain MongoDB operators
        if (value instanceof Map) {
            Map<String, Object> operatorMap = (Map<String, Object>) value;

            for (Map.Entry<String, Object> operatorEntry : operatorMap.entrySet()) {
                String operator = operatorEntry.getKey();
                Object operatorValue = operatorEntry.getValue();

                switch (operator) {
                    case "$in" -> criteria = criteria.in((List<?>) operatorValue);
                    case "$nin" -> criteria = criteria.nin((List<?>) operatorValue);
                    case "$gt" -> criteria = criteria.gt(operatorValue);
                    case "$gte" -> criteria = criteria.gte(operatorValue);
                    case "$lt" -> criteria = criteria.lt(operatorValue);
                    case "$lte" -> criteria = criteria.lte(operatorValue);
                    case "$ne" -> criteria = criteria.ne(operatorValue);
                    case "$regex" -> criteria = criteria.regex(operatorValue.toString());
                    case "$exists" -> criteria = criteria.exists((Boolean) operatorValue);
                    // For unrecognised operators, fall back to matching the document as a value
                    default -> criteria = criteria.is(value);
                }
            }
        } else {
            criteria = criteria.is(value);
        }

        return criteria;
    }

    // ==================== AGGREGATIONS ====================

    @Override
    public List<ListingWithReviewsResult> getListingsWithRecentReviews(Integer limit, String listingId) {
        int resultLimit = Math.clamp(limit != null ? limit : 10, 1, 50);

        // Only consider listings that actually carry reviews. "reviews.0" exists is the
        // idiomatic way to say "this array is non-empty" without computing its size.
        Criteria matchCriteria = Criteria.where(Listing.Fields.REVIEWS + ".0").exists(true)
                .and(Listing.Fields.LAST_REVIEW).ne(null);

        if (isPresent(listingId)) {
            matchCriteria = matchCriteria.and(Listing.Fields.ID).is(listingId.trim());
        }

        // This pipeline demonstrates $unwind, $group and $slice. In a dataset where reviews live
        // in their own collection this report would use $lookup instead; here the reviews are
        // embedded, so the array is flattened, sorted and regrouped.
        //
        // The $sort/$limit on the indexed last_review field runs BEFORE $unwind so that only the
        // handful of listings we are going to return get expanded, rather than every review in
        // the collection.
        Aggregation aggregation = Aggregation.newAggregation(
                // STAGE 1: Narrow to reviewed listings (and optionally to a single listing)
                Aggregation.match(matchCriteria),

                // STAGE 2: Order by review recency using the last_review index
                Aggregation.sort(Sort.Direction.DESC, Listing.Fields.LAST_REVIEW),

                // STAGE 3: Keep only the listings that will be returned
                Aggregation.limit(resultLimit),

                // STAGE 4: Flatten the embedded reviews array into one document per review
                Aggregation.unwind(Listing.Fields.REVIEWS),

                // STAGE 5: Order reviews newest-first within each listing
                Aggregation.sort(Sort.by(
                        Sort.Order.asc(Listing.Fields.ID),
                        Sort.Order.desc(Listing.Fields.REVIEWS + ".date"))),

                // STAGE 6: Regroup by listing, collecting the ordered reviews back into an array
                Aggregation.group(Listing.Fields.ID)
                        .first(Listing.Fields.NAME).as("name")
                        .first(Listing.Fields.PROPERTY_TYPE).as("propertyType")
                        .first(Listing.Fields.ADDRESS_MARKET).as("market")
                        .first(Listing.Fields.PRICE).as("price")
                        .first(Listing.Fields.IMAGES_PICTURE_URL).as("pictureUrl")
                        .first(Listing.Fields.REVIEW_SCORES_RATING).as("reviewScore")
                        .push(Listing.Fields.REVIEWS).as("reviews")
                        .count().as("totalReviews"),

                // STAGE 7: Shape the response, keeping only the five most recent reviews
                Aggregation.project()
                        .and("_id").as("_id")
                        .and("name").as("name")
                        .and("propertyType").as("propertyType")
                        .and("market").as("market")
                        .and("price").as("price")
                        .and("pictureUrl").as("pictureUrl")
                        .and("reviewScore").as("reviewScore")
                        .and(ArrayOperators.Slice.sliceArrayOf("reviews").itemCount(5)).as("recentReviews")
                        .and("totalReviews").as("totalReviews")
                        .and(ArrayOperators.ArrayElemAt.arrayOf("reviews.date").elementAt(0))
                                .as("mostRecentReviewDate"),

                // STAGE 8: $group does not preserve order, so restore it for the response
                Aggregation.sort(Sort.Direction.DESC, "mostRecentReviewDate")
        );

        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);

        return results.getMappedResults().stream()
                .map(this::toListingWithReviewsResult)
                .collect(Collectors.toList());
    }

    @Override
    public List<PropertyTypeStatisticsResult> getPropertyTypeStatistics(Integer limit) {
        int resultLimit = Math.clamp(limit != null ? limit : 20, 1, 100);

        // This pipeline demonstrates $group with statistical accumulators.
        // Prices are stored as Decimal128; converting them to doubles up front keeps $avg,
        // $min and $max returning a single, predictable numeric type.
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(
                        Criteria.where(Listing.Fields.PROPERTY_TYPE).exists(true).ne(null)
                                .and(Listing.Fields.PRICE).exists(true).ne(null)
                ),

                Aggregation.project(Listing.Fields.PROPERTY_TYPE)
                        .and(toDouble(Listing.Fields.PRICE)).as("priceValue")
                        .and(Listing.Fields.REVIEW_SCORES_RATING).as("ratingValue")
                        .and(Listing.Fields.ACCOMMODATES).as("accommodatesValue")
                        .and(Listing.Fields.NUMBER_OF_REVIEWS).as("reviewsValue"),

                Aggregation.group(Listing.Fields.PROPERTY_TYPE)
                        .count().as("listingCount")
                        .avg("priceValue").as("averagePrice")
                        .max("priceValue").as("highestPrice")
                        .min("priceValue").as("lowestPrice")
                        .avg("ratingValue").as("averageRating")
                        .avg("accommodatesValue").as("averageAccommodates")
                        .sum("reviewsValue").as("totalReviews"),

                Aggregation.sort(Sort.Direction.DESC, "listingCount"),
                Aggregation.limit(resultLimit)
        );

        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);

        return results.getMappedResults().stream()
                .map(doc -> PropertyTypeStatisticsResult.builder()
                        .propertyType(asString(doc.get("_id")))
                        .listingCount(toInteger(doc.get("listingCount")))
                        .averagePrice(round(toDouble(doc.get("averagePrice")), 2))
                        .highestPrice(round(toDouble(doc.get("highestPrice")), 2))
                        .lowestPrice(round(toDouble(doc.get("lowestPrice")), 2))
                        .averageRating(round(toDouble(doc.get("averageRating")), 2))
                        .averageAccommodates(round(toDouble(doc.get("averageAccommodates")), 1))
                        .totalReviews(toLong(doc.get("totalReviews")))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<AmenityStatisticsResult> getAmenityStatistics(Integer limit) {
        int resultLimit = Math.clamp(limit != null ? limit : 20, 1, 100);

        // This pipeline demonstrates $unwind for array flattening followed by $group
        Aggregation aggregation = Aggregation.newAggregation(
                // STAGE 1: Only listings that declare amenities
                Aggregation.match(
                        Criteria.where(Listing.Fields.AMENITIES + ".0").exists(true)
                ),

                // STAGE 2: Project just the fields the report needs, normalising price to a double
                Aggregation.project(Listing.Fields.AMENITIES)
                        .and(toDouble(Listing.Fields.PRICE)).as("priceValue")
                        .and(Listing.Fields.REVIEW_SCORES_RATING).as("ratingValue"),

                // STAGE 3: One document per (listing, amenity) pair
                Aggregation.unwind(Listing.Fields.AMENITIES),

                // STAGE 4: Drop blank amenity values
                Aggregation.match(Criteria.where(Listing.Fields.AMENITIES).ne(null).ne("")),

                // STAGE 5: Count listings per amenity and average their price and rating
                Aggregation.group(Listing.Fields.AMENITIES)
                        .count().as("listingCount")
                        .avg("priceValue").as("averagePrice")
                        .avg("ratingValue").as("averageRating"),

                // STAGE 6: Most common amenities first
                Aggregation.sort(Sort.Direction.DESC, "listingCount"),
                Aggregation.limit(resultLimit)
        );

        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);

        return results.getMappedResults().stream()
                .map(doc -> AmenityStatisticsResult.builder()
                        .amenity(asString(doc.get("_id")))
                        .listingCount(toInteger(doc.get("listingCount")))
                        .averagePrice(round(toDouble(doc.get("averagePrice")), 2))
                        .averageRating(round(toDouble(doc.get("averageRating")), 2))
                        .build())
                .collect(Collectors.toList());
    }

    // ==================== GEOSPATIAL ====================

    @Override
    public List<NearbyListingResult> findNearbyListings(Double longitude, Double latitude,
                                                        Integer maxDistanceMeters, Integer limit) {
        if (longitude == null || latitude == null) {
            throw new ValidationException("Both longitude and latitude are required");
        }
        if (longitude < -180 || longitude > 180) {
            throw new ValidationException("Longitude must be between -180 and 180");
        }
        if (latitude < -90 || latitude > 90) {
            throw new ValidationException("Latitude must be between -90 and 90");
        }

        int radius = Math.clamp(maxDistanceMeters != null ? maxDistanceMeters : 5000, 1, 200_000);
        int resultLimit = Math.clamp(limit != null ? limit : 20, 1, 100);

        // $geoNear must be the first stage of a pipeline and cannot be expressed through the
        // Spring Data Aggregation builder without losing the 'key' option, so the pipeline is
        // written with the driver's Document API. It requires the 2dsphere index that
        // DatabaseVerification creates on address.location.
        Document geoNearStage = new Document("$geoNear", new Document()
                .append("near", new Document("type", "Point")
                        .append("coordinates", List.of(longitude, latitude)))
                .append("distanceField", "distanceMeters")
                .append("maxDistance", radius)
                .append("spherical", true)
                .append("key", Listing.Fields.ADDRESS_LOCATION)
        );

        // $geoNear already returns results sorted by distance ascending
        Document limitStage = new Document("$limit", resultLimit);

        Document projectStage = new Document("$project", new Document()
                .append(Listing.Fields.ID, 1)
                .append(Listing.Fields.NAME, 1)
                .append(Listing.Fields.PROPERTY_TYPE, 1)
                .append(Listing.Fields.ROOM_TYPE, 1)
                .append(Listing.Fields.PRICE, 1)
                .append(Listing.Fields.ACCOMMODATES, 1)
                .append(Listing.Fields.IMAGES_PICTURE_URL, 1)
                .append(Listing.Fields.REVIEW_SCORES_RATING, 1)
                .append(Listing.Fields.ADDRESS_MARKET, 1)
                .append("distanceMeters", 1)
        );

        try {
            List<Document> pipeline = List.of(geoNearStage, limitStage, projectStage);

            List<NearbyListingResult> results = new ArrayList<>();
            mongoTemplate.getCollection(COLLECTION)
                    .aggregate(pipeline)
                    .forEach(doc -> results.add(toNearbyListingResult(doc)));

            return results;
        } catch (Exception e) {
            throw new DatabaseOperationException("Error performing proximity search: " + e.getMessage());
        }
    }

    // ==================== MONGODB SEARCH ====================

    @Override
    public List<Listing> searchListings(ListingSearchRequest searchRequest) {
        if (!searchRequest.hasSearchFields()) {
            throw new ValidationException("At least one search parameter must be provided");
        }

        String operator = searchRequest.searchOperator() != null ? searchRequest.searchOperator() : "must";

        if (!operator.equals("must") && !operator.equals("should")
                && !operator.equals("mustNot") && !operator.equals("filter")) {
            throw new ValidationException(
                "Invalid searchOperator '" + operator + "'. " +
                "The searchOperator must be one of: must, should, mustNot, filter"
            );
        }

        int resultLimit = Math.clamp(searchRequest.limit() != null ? searchRequest.limit() : 20, 1, 100);
        int resultSkip = Math.max(searchRequest.skip() != null ? searchRequest.skip() : 0, 0);

        List<Document> searchClauses = new ArrayList<>();

        // Long-form prose fields use the phrase operator so that multi-word queries have to
        // appear as an actual phrase rather than as scattered terms
        addPhraseClause(searchClauses, searchRequest.summary(), Listing.Fields.SUMMARY);
        addPhraseClause(searchClauses, searchRequest.description(), Listing.Fields.DESCRIPTION);
        addPhraseClause(searchClauses, searchRequest.neighborhood(), Listing.Fields.NEIGHBORHOOD_OVERVIEW);

        // Short, name-like fields use a scoring hierarchy so that exact matches outrank
        // typo-tolerant ones
        addFuzzyClause(searchClauses, searchRequest.name(), Listing.Fields.NAME);
        addFuzzyClause(searchClauses, searchRequest.host(), Listing.Fields.HOST_NAME);
        addFuzzyClause(searchClauses, searchRequest.amenities(), Listing.Fields.AMENITIES);

        Document searchStage = new Document("$search", new Document()
                .append("index", SEARCH_INDEX)
                .append("compound", new Document(operator, searchClauses))
        );

        Document skipStage = new Document("$skip", resultSkip);
        Document limitStage = new Document("$limit", resultLimit);

        // Exclude the embedding rather than listing every field to include, so new fields on the
        // document show up in search results automatically
        Document projectStage = new Document("$project", new Document(embeddingField, 0));

        try {
            List<Document> pipeline = List.of(searchStage, skipStage, limitStage, projectStage);

            return mongoTemplate.getCollection(COLLECTION)
                    .aggregate(pipeline)
                    .map(doc -> mongoTemplate.getConverter().read(Listing.class, doc))
                    .into(new ArrayList<>());
        } catch (Exception e) {
            throw new DatabaseOperationException("Error performing MongoDB Search: " + e.getMessage());
        }
    }

    /**
     * Adds a phrase clause for exact multi-word matching on a prose field.
     */
    private void addPhraseClause(List<Document> clauses, String query, String path) {
        if (!isPresent(query)) {
            return;
        }
        clauses.add(new Document("phrase", new Document()
                .append("query", query.trim())
                .append("path", path)));
    }

    /**
     * Adds a compound clause that scores matches on a short field in three tiers:
     * <ol>
     *   <li>phrase match (highest score) - the exact phrase</li>
     *   <li>text match without fuzzy (high score) - all terms, exact spelling</li>
     *   <li>text match with fuzzy (lower score) - typo-tolerant fallback</li>
     * </ol>
     * See https://www.mongodb.com/docs/atlas/atlas-search/operators-collectors/text/
     */
    private void addFuzzyClause(List<Document> clauses, String query, String path) {
        if (!isPresent(query)) {
            return;
        }

        String trimmed = query.trim();

        clauses.add(new Document("compound", new Document()
                .append("should", Arrays.asList(
                        new Document("phrase", new Document()
                                .append("query", trimmed)
                                .append("path", path)),
                        new Document("text", new Document()
                                .append("query", trimmed)
                                .append("path", path)
                                .append("matchCriteria", "all")),
                        new Document("text", new Document()
                                .append("query", trimmed)
                                .append("path", path)
                                .append("matchCriteria", "all")
                                // Allow up to 1 edit, requiring the first 2 characters to match
                                .append("fuzzy", new Document()
                                        .append("maxEdits", 1)
                                        .append("prefixLength", 2)))
                ))
                .append("minimumShouldMatch", 1)
        ));
    }

    // ==================== VECTOR SEARCH ====================

    @Override
    public List<VectorSearchResult> vectorSearchListings(String query, Integer limit) {
        if (!isPresent(query)) {
            throw new ValidationException("Search query is required");
        }

        requireVoyageApiKey();

        int resultLimit = Math.clamp(limit != null ? limit : 10, 1, 50);

        try {
            List<Double> queryVector = generateEmbeddings(List.of(query.trim()), "query").get(0);

            Document vectorSearchStage = new Document("$vectorSearch", new Document()
                    .append("index", VECTOR_INDEX)
                    .append("path", embeddingField)
                    .append("queryVector", queryVector)
                    // Searching roughly 20x the requested limit improves recall
                    .append("numCandidates", resultLimit * 20)
                    .append("limit", resultLimit)
            );

            Document projectStage = new Document("$project", new Document()
                    .append(Listing.Fields.ID, 1)
                    .append(Listing.Fields.NAME, 1)
                    .append(Listing.Fields.SUMMARY, 1)
                    .append(Listing.Fields.PROPERTY_TYPE, 1)
                    .append(Listing.Fields.ROOM_TYPE, 1)
                    .append(Listing.Fields.PRICE, 1)
                    .append(Listing.Fields.AMENITIES, 1)
                    .append(Listing.Fields.IMAGES_PICTURE_URL, 1)
                    .append(Listing.Fields.ADDRESS_MARKET, 1)
                    .append("score", new Document("$meta", "vectorSearchScore"))
            );

            List<Document> pipeline = List.of(vectorSearchStage, projectStage);

            List<VectorSearchResult> results = new ArrayList<>();
            mongoTemplate.getCollection(COLLECTION)
                    .aggregate(pipeline)
                    .forEach(doc -> results.add(toVectorSearchResult(doc)));

            return results;

        } catch (VoyageAuthException | VoyageAPIException e) {
            // Surface Voyage AI failures unchanged so the handler can map them precisely
            throw e;
        } catch (IOException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Network error calling Voyage AI API";
            throw new VoyageAPIException("Error performing vector search: " + errorMsg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Vector search was interrupted");
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new DatabaseOperationException("Error performing vector search: " + errorMsg);
        }
    }

    @Override
    public List<Listing> findSimilarListings(String listingId, Integer limit) {
        String id = requireListingId(listingId);
        int resultLimit = Math.clamp(limit != null ? limit : 10, 1, 50);

        // Read only the embedding: the rest of the source document is not needed here
        Document source = mongoTemplate.getCollection(COLLECTION)
                .find(new Document(Listing.Fields.ID, id))
                .projection(new Document(embeddingField, 1))
                .first();

        if (source == null) {
            throw new ResourceNotFoundException("Listing not found");
        }

        if (!source.containsKey(embeddingField)) {
            throw new ValidationException(
                "This listing has no description embedding yet. Call POST /api/listings/embeddings/backfill "
                + "to generate embeddings before using similarity search.");
        }

        @SuppressWarnings("unchecked")
        List<Double> embedding = (List<Double>) source.get(embeddingField);

        Document vectorSearchStage = new Document("$vectorSearch", new Document()
                .append("index", VECTOR_INDEX)
                .append("path", embeddingField)
                .append("queryVector", embedding)
                .append("numCandidates", resultLimit * 20)
                // +1 because the source listing is its own nearest neighbour
                .append("limit", resultLimit + 1)
        );

        Document matchStage = new Document("$match",
                new Document(Listing.Fields.ID, new Document("$ne", id)));

        Document limitStage = new Document("$limit", resultLimit);

        Document projectStage = new Document("$project", new Document()
                .append(embeddingField, 0));

        try {
            List<Document> pipeline = List.of(vectorSearchStage, matchStage, limitStage, projectStage);

            return mongoTemplate.getCollection(COLLECTION)
                    .aggregate(pipeline)
                    .map(doc -> mongoTemplate.getConverter().read(Listing.class, doc))
                    .into(new ArrayList<>());
        } catch (Exception e) {
            throw new DatabaseOperationException("Error performing vector search: " + e.getMessage());
        }
    }

    @Override
    public EmbeddingBackfillResponse backfillDescriptionEmbeddings(Integer limit) {
        requireVoyageApiKey();

        int resultLimit = Math.clamp(limit != null ? limit : 50, 1, 200);

        Query pending = new Query(Criteria.where(embeddingField).exists(false));
        pending.limit(resultLimit);
        pending.fields()
                .include(Listing.Fields.ID)
                .include(Listing.Fields.NAME)
                .include(Listing.Fields.SUMMARY)
                .include(Listing.Fields.DESCRIPTION)
                .include(Listing.Fields.NEIGHBORHOOD_OVERVIEW)
                .include(Listing.Fields.PROPERTY_TYPE)
                .include(Listing.Fields.AMENITIES)
                .include(Listing.Fields.ADDRESS_MARKET);

        List<Document> candidates = mongoTemplate.find(pending, Document.class, COLLECTION);

        List<String> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        int skipped = 0;

        for (Document candidate : candidates) {
            String text = buildEmbeddingText(candidate);
            if (text.isEmpty()) {
                skipped++;
                continue;
            }
            ids.add(asString(candidate.get(Listing.Fields.ID)));
            texts.add(text);
        }

        int embedded = 0;

        try {
            for (int start = 0; start < texts.size(); start += EMBEDDING_BATCH_SIZE) {
                int end = Math.min(start + EMBEDDING_BATCH_SIZE, texts.size());
                List<String> batchTexts = texts.subList(start, end);
                List<String> batchIds = ids.subList(start, end);

                List<List<Double>> vectors = generateEmbeddings(batchTexts, "document");

                for (int i = 0; i < batchIds.size(); i++) {
                    Query byId = new Query(Criteria.where(Listing.Fields.ID).is(batchIds.get(i)));
                    Update update = new Update().set(embeddingField, vectors.get(i));
                    mongoTemplate.updateFirst(byId, update, COLLECTION);
                    embedded++;
                }
            }
        } catch (VoyageAuthException | VoyageAPIException e) {
            throw e;
        } catch (IOException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Network error calling Voyage AI API";
            throw new VoyageAPIException("Error generating embeddings: " + errorMsg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Embedding backfill was interrupted");
        }

        long remaining = mongoTemplate.count(
                new Query(Criteria.where(embeddingField).exists(false)), COLLECTION);

        return EmbeddingBackfillResponse.builder()
                .embeddedCount(embedded)
                .skippedCount(skipped)
                .remainingCount(remaining)
                .embeddingField(embeddingField)
                .dimensions(embeddingDimensions)
                .build();
    }

    /**
     * Builds the text that represents a listing to the embedding model.
     *
     * <p>The description alone loses useful signal, so the name, property type, market and
     * amenities are folded in. That is what lets a query like "quiet cabin near the beach with
     * a fireplace" match on more than prose alone.
     */
    private String buildEmbeddingText(Document listing) {
        List<String> parts = new ArrayList<>();

        addIfPresent(parts, asString(listing.get(Listing.Fields.NAME)));
        addIfPresent(parts, asString(listing.get(Listing.Fields.PROPERTY_TYPE)));

        Document address = listing.get(Listing.Fields.ADDRESS, Document.class);
        if (address != null) {
            addIfPresent(parts, asString(address.get("market")));
        }

        addIfPresent(parts, asString(listing.get(Listing.Fields.SUMMARY)));
        addIfPresent(parts, asString(listing.get(Listing.Fields.DESCRIPTION)));
        addIfPresent(parts, asString(listing.get(Listing.Fields.NEIGHBORHOOD_OVERVIEW)));

        List<?> amenities = listing.get(Listing.Fields.AMENITIES, List.class);
        if (amenities != null && !amenities.isEmpty()) {
            addIfPresent(parts, "Amenities: " + amenities.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ")));
        }

        return String.join("\n", parts).trim();
    }

    private void addIfPresent(List<String> parts, String value) {
        if (isPresent(value)) {
            parts.add(value.trim());
        }
    }

    private void requireVoyageApiKey() {
        if (!isPresent(voyageApiKey) || voyageApiKey.equals("your_voyage_api_key")) {
            throw new ServiceUnavailableException(
                "Vector search unavailable: VOYAGE_API_KEY not configured. Please add your API key to the .env file"
            );
        }
    }

    /**
     * Generates embeddings for one or more texts using the Voyage AI REST API.
     *
     * <p>The request body is built with Jackson rather than string concatenation so that quotes,
     * newlines and other control characters inside listing descriptions cannot produce invalid
     * JSON. The output dimension is requested explicitly so it always matches the vector search
     * index definition.
     *
     * @param texts the texts to embed, in order
     * @param inputType "query" for search queries, "document" for stored content
     * @return one embedding per input text, in the same order
     */
    private List<List<Double>> generateEmbeddings(List<String> texts, String inputType)
            throws IOException, InterruptedException {

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);
        body.put("model", embeddingModel);
        body.put("output_dimension", embeddingDimensions);
        body.put("input_type", inputType);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.voyageai.com/v1/embeddings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + voyageApiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            if (response.statusCode() == 401) {
                throw new VoyageAuthException(
                    "Invalid Voyage AI API key. Please check your VOYAGE_API_KEY in the .env file");
            }
            throw new VoyageAPIException(
                "Voyage AI API returned status code " + response.statusCode() + ": " + response.body(),
                response.statusCode()
            );
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode dataNode = root.get("data");

        if (dataNode == null || !dataNode.isArray() || dataNode.isEmpty()) {
            throw new IOException(
                "Invalid Voyage AI API response: 'data' is missing or empty. Response: " + response.body());
        }

        // Voyage returns each embedding with the index of the input it belongs to. Sorting by
        // that index keeps embeddings aligned with their listings even if the API reorders them.
        List<JsonNode> ordered = new ArrayList<>();
        dataNode.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(node -> node.path("index").asInt(0)));

        List<List<Double>> embeddings = new ArrayList<>(ordered.size());

        for (JsonNode element : ordered) {
            JsonNode embeddingNode = element.get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new IOException(
                    "Invalid Voyage AI API response: 'embedding' is missing or not an array");
            }

            List<Double> embedding = new ArrayList<>(embeddingNode.size());
            for (JsonNode value : embeddingNode) {
                embedding.add(value.asDouble());
            }
            embeddings.add(embedding);
        }

        if (embeddings.size() != texts.size()) {
            throw new IOException("Voyage AI returned " + embeddings.size()
                    + " embeddings for " + texts.size() + " inputs");
        }

        return embeddings;
    }

    // ==================== MAPPING HELPERS ====================

    /**
     * Builds a Listing entity from a create request, assembling the nested subdocuments.
     *
     * <p>A string id is generated up front so that documents created by this application keep
     * the same {@code _id} type as the ones shipped with the dataset. Letting Spring Data
     * generate the id would store an ObjectId instead, which would make {@code $in} filters
     * behave inconsistently across old and new documents.
     */
    private Listing toListing(CreateListingRequest request) {
        Listing.Images images = isPresent(request.pictureUrl())
                ? Listing.Images.builder().pictureUrl(request.pictureUrl()).build()
                : null;

        Listing.Host host = isPresent(request.hostName())
                ? Listing.Host.builder().hostName(request.hostName()).build()
                : null;

        Listing.Location location = null;
        if (request.longitude() != null && request.latitude() != null) {
            location = Listing.Location.builder()
                    .type("Point")
                    // GeoJSON order: longitude first, then latitude
                    .coordinates(List.of(request.longitude(), request.latitude()))
                    .isLocationExact(Boolean.TRUE)
                    .build();
        }

        Listing.Address address = null;
        if (isPresent(request.market()) || isPresent(request.country())
                || isPresent(request.suburb()) || location != null) {
            address = Listing.Address.builder()
                    .market(request.market())
                    .country(request.country())
                    .suburb(request.suburb())
                    .location(location)
                    .build();
        }

        return Listing.builder()
                .id(new ObjectId().toHexString())
                .name(request.name())
                .summary(request.summary())
                .description(request.description())
                .neighborhoodOverview(request.neighborhoodOverview())
                .propertyType(request.propertyType())
                .roomType(request.roomType())
                .bedType(request.bedType())
                .cancellationPolicy(request.cancellationPolicy())
                .accommodates(request.accommodates())
                .bedrooms(request.bedrooms())
                .beds(request.beds())
                .bathrooms(request.bathrooms())
                .amenities(request.amenities())
                .price(request.price())
                .cleaningFee(request.cleaningFee())
                .minimumNights(request.minimumNights())
                .maximumNights(request.maximumNights())
                .numberOfReviews(0)
                .reviews(List.of())
                .images(images)
                .host(host)
                .address(address)
                .build();
    }

    /**
     * Collects the non-null fields of an update request into MongoDB field paths.
     *
     * <p>A generic ObjectMapper conversion cannot be used here: the JSON property names are
     * camelCase while the stored fields are snake_case and partly nested, so each property is
     * mapped explicitly through {@link #FIELD_PATHS}.
     */
    private Map<String, Object> toUpdateMap(UpdateListingRequest request) {
        Map<String, Object> raw = new LinkedHashMap<>();

        raw.put("name", request.name());
        raw.put("summary", request.summary());
        raw.put("description", request.description());
        raw.put("neighborhoodOverview", request.neighborhoodOverview());
        raw.put("propertyType", request.propertyType());
        raw.put("roomType", request.roomType());
        raw.put("bedType", request.bedType());
        raw.put("cancellationPolicy", request.cancellationPolicy());
        raw.put("accommodates", request.accommodates());
        raw.put("bedrooms", request.bedrooms());
        raw.put("beds", request.beds());
        raw.put("bathrooms", request.bathrooms());
        raw.put("amenities", request.amenities());
        raw.put("price", request.price());
        raw.put("cleaningFee", request.cleaningFee());
        raw.put("minimumNights", request.minimumNights());
        raw.put("maximumNights", request.maximumNights());
        raw.put("pictureUrl", request.pictureUrl());
        raw.put("hostName", request.hostName());
        raw.put("market", request.market());
        raw.put("country", request.country());
        raw.put("suburb", request.suburb());

        Map<String, Object> updates = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (value != null) {
                updates.put(toMongoPath(key), value);
            }
        });

        return updates;
    }

    private ListingWithReviewsResult toListingWithReviewsResult(Document doc) {
        List<ListingWithReviewsResult.ReviewInfo> recentReviews = null;

        @SuppressWarnings("unchecked")
        List<Document> reviewDocs = (List<Document>) doc.get("recentReviews");

        if (reviewDocs != null) {
            recentReviews = reviewDocs.stream()
                    .map(reviewDoc -> ListingWithReviewsResult.ReviewInfo.builder()
                            .id(asString(reviewDoc.get("_id")))
                            .reviewerName(reviewDoc.getString("reviewer_name"))
                            .comments(reviewDoc.getString("comments"))
                            .date(toInstant(reviewDoc.get("date")))
                            .build())
                    .collect(Collectors.toList());
        }

        return ListingWithReviewsResult.builder()
                ._id(asString(doc.get("_id")))
                .name(doc.getString("name"))
                .propertyType(doc.getString("propertyType"))
                .market(doc.getString("market"))
                .price(toBigDecimal(doc.get("price")))
                .pictureUrl(doc.getString("pictureUrl"))
                .reviewScore(toInteger(doc.get("reviewScore")))
                .recentReviews(recentReviews)
                .totalReviews(toInteger(doc.get("totalReviews")))
                .mostRecentReviewDate(toInstant(doc.get("mostRecentReviewDate")))
                .build();
    }

    private NearbyListingResult toNearbyListingResult(Document doc) {
        Document images = doc.get(Listing.Fields.IMAGES, Document.class);
        Document address = doc.get(Listing.Fields.ADDRESS, Document.class);
        Document reviewScores = doc.get(Listing.Fields.REVIEW_SCORES, Document.class);

        return NearbyListingResult.builder()
                ._id(asString(doc.get(Listing.Fields.ID)))
                .name(doc.getString(Listing.Fields.NAME))
                .propertyType(doc.getString(Listing.Fields.PROPERTY_TYPE))
                .roomType(doc.getString(Listing.Fields.ROOM_TYPE))
                .price(toBigDecimal(doc.get(Listing.Fields.PRICE)))
                .accommodates(toInteger(doc.get(Listing.Fields.ACCOMMODATES)))
                .pictureUrl(images != null ? images.getString("picture_url") : null)
                .market(address != null ? address.getString("market") : null)
                .reviewScore(reviewScores != null ? toInteger(reviewScores.get("review_scores_rating")) : null)
                .distanceMeters(round(toDouble(doc.get("distanceMeters")), 1))
                .build();
    }

    private VectorSearchResult toVectorSearchResult(Document doc) {
        Document images = doc.get(Listing.Fields.IMAGES, Document.class);
        Document address = doc.get(Listing.Fields.ADDRESS, Document.class);

        @SuppressWarnings("unchecked")
        List<String> amenities = (List<String>) doc.get(Listing.Fields.AMENITIES);

        return VectorSearchResult.builder()
                .id(asString(doc.get(Listing.Fields.ID)))
                .name(doc.getString(Listing.Fields.NAME))
                .summary(doc.getString(Listing.Fields.SUMMARY))
                .propertyType(doc.getString(Listing.Fields.PROPERTY_TYPE))
                .roomType(doc.getString(Listing.Fields.ROOM_TYPE))
                .price(toBigDecimal(doc.get(Listing.Fields.PRICE)))
                .pictureUrl(images != null ? images.getString("picture_url") : null)
                .market(address != null ? address.getString("market") : null)
                .amenities(amenities)
                .score(toDouble(doc.get("score")))
                .build();
    }

    // ==================== SMALL UTILITIES ====================

    /**
     * Validates and normalises a listing id.
     *
     * <p>{@code sample_airbnb} uses opaque string ids, so there is no format to check beyond
     * requiring a non-blank value. This is deliberately looser than an ObjectId-keyed
     * collection would be.
     */
    private String requireListingId(String id) {
        if (!isPresent(id)) {
            throw new ValidationException("Listing ID is required");
        }
        return id.trim();
    }

    private List<String> distinctSortedStrings(String field) {
        List<String> values = mongoTemplate.findDistinct(new Query(), field, COLLECTION, String.class);

        return values.stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isEmpty())
                .sorted(String::compareTo)
                .collect(Collectors.toList());
    }

    /**
     * Translates an API property name into its MongoDB field path, passing through anything
     * that is already a MongoDB path.
     */
    private static String toMongoPath(String key) {
        return FIELD_PATHS.getOrDefault(key, key);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Builds a {@code $toDouble} expression for the given field.
     *
     * <p>Prices are stored as Decimal128. Converting them before the accumulators run means
     * {@code $avg}, {@code $min} and {@code $max} all return plain doubles, which keeps the
     * result mapping below simple and predictable.
     */
    private static AggregationExpression toDouble(String field) {
        return context -> new Document("$toDouble", "$" + field);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue().doubleValue();
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue().intValue();
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue().longValue();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return null;
    }

    private static java.time.Instant toInstant(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof java.time.Instant instant) {
            return instant;
        }
        return null;
    }

    private static Double round(Double value, int decimals) {
        if (value == null) {
            return null;
        }
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
