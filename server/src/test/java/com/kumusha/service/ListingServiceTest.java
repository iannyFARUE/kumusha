package com.kumusha.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kumusha.exception.ResourceNotFoundException;
import com.kumusha.exception.ServiceUnavailableException;
import com.kumusha.exception.ValidationException;
import com.kumusha.model.Listing;
import com.kumusha.model.dto.AmenityStatisticsResult;
import com.kumusha.model.dto.BatchInsertResponse;
import com.kumusha.model.dto.BatchUpdateResponse;
import com.kumusha.model.dto.CreateListingRequest;
import com.kumusha.model.dto.DeleteResponse;
import com.kumusha.model.dto.ListingSearchQuery;
import com.kumusha.model.dto.ListingSearchRequest;
import com.kumusha.model.dto.ListingsPageResponse;
import com.kumusha.model.dto.PropertyTypeStatisticsResult;
import com.kumusha.model.dto.UpdateListingRequest;
import com.kumusha.repository.ListingRepository;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for ListingServiceImpl.
 *
 * <p>These tests verify the business logic of the service layer by mocking the repository and
 * MongoTemplate dependencies. They pay particular attention to the two places where the
 * sample_airbnb dataset departs from a conventional MongoDB collection: string ids, and the
 * camelCase-to-snake_case field translation applied on every write path.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ListingService Unit Tests")
class ListingServiceTest {

    private static final String COLLECTION = "listingsAndReviews";
    private static final String EMBEDDING_FIELD = "description_embedding";

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ListingServiceImpl listingService;

    private String testId;
    private Listing testListing;
    private CreateListingRequest createRequest;

    @BeforeEach
    void setUp() {
        testId = "10006546";

        testListing = Listing.builder()
                .id(testId)
                .name("Ribeira Charming Duplex")
                .propertyType("House")
                .accommodates(8)
                .price(new BigDecimal("80.00"))
                .build();

        createRequest = CreateListingRequest.builder()
                .name("Sunny Loft in Kopje")
                .propertyType("Loft")
                .accommodates(2)
                .price(new BigDecimal("45.00"))
                .build();

        // @Value fields are not populated outside a Spring context
        ReflectionTestUtils.setField(listingService, "embeddingField", EMBEDDING_FIELD);
        ReflectionTestUtils.setField(listingService, "embeddingDimensions", 2048);
        ReflectionTestUtils.setField(listingService, "embeddingModel", "voyage-3-large");
    }

    // ==================== GET ALL LISTINGS TESTS ====================

    @Test
    @DisplayName("Should get all listings with default pagination")
    void testGetAllListings_WithDefaults() {
        when(mongoTemplate.find(any(Query.class), eq(Listing.class))).thenReturn(List.of(testListing));
        when(mongoTemplate.count(any(Query.class), eq(Listing.class))).thenReturn(57L);

        ListingsPageResponse result = listingService.getAllListings(ListingSearchQuery.builder().build());

        assertNotNull(result);
        assertEquals(1, result.listings().size());
        assertEquals(testListing.getName(), result.listings().get(0).getName());

        // The total describes the whole match, not the page, which is the entire point of
        // returning it alongside the rows
        assertEquals(57L, result.totalCount());
        assertEquals(20, result.limit());
        assertEquals(0, result.skip());

        verify(mongoTemplate).find(any(Query.class), eq(Listing.class));
    }

    @Test
    @DisplayName("Should count the filters without the paging applied")
    void testGetAllListings_CountsBeforePaging() {
        // The service counts and then pages the same Query instance, so an ArgumentCaptor would
        // hand back the object after paging was applied. The state has to be read during the call.
        int[] pagingAtCountTime = new int[2];

        when(mongoTemplate.find(any(Query.class), eq(Listing.class))).thenReturn(List.of(testListing));
        when(mongoTemplate.count(any(Query.class), eq(Listing.class))).thenAnswer(invocation -> {
            Query counted = invocation.getArgument(0);
            pagingAtCountTime[0] = counted.getLimit() > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) counted.getLimit();
            pagingAtCountTime[1] = (int) counted.getSkip();
            return 999L;
        });

        ListingsPageResponse result =
                listingService.getAllListings(ListingSearchQuery.builder().limit(5).skip(40).build());

        // A count that inherited skip and limit would cap out at the page size and make the
        // reported total meaningless
        assertEquals(0, pagingAtCountTime[0], "count query must not carry a limit");
        assertEquals(0, pagingAtCountTime[1], "count query must not carry a skip");

        // The total is the whole match, not the five rows the page asked for
        assertEquals(999L, result.totalCount());
    }

    @Test
    @DisplayName("Should clamp the limit to a maximum of 100")
    void testGetAllListings_EnforcesMaxLimit() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.find(queryCaptor.capture(), eq(Listing.class)))
                .thenReturn(Collections.emptyList());

        listingService.getAllListings(ListingSearchQuery.builder().limit(500).build());

        assertEquals(100, queryCaptor.getValue().getLimit());
    }

    @Test
    @DisplayName("Should clamp the limit to a minimum of 1")
    void testGetAllListings_EnforcesMinLimit() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.find(queryCaptor.capture(), eq(Listing.class)))
                .thenReturn(Collections.emptyList());

        listingService.getAllListings(ListingSearchQuery.builder().limit(0).build());

        assertEquals(1, queryCaptor.getValue().getLimit());
    }

    @Test
    @DisplayName("Should never return the embedding vector to callers")
    void testGetAllListings_ExcludesEmbedding() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.find(queryCaptor.capture(), eq(Listing.class)))
                .thenReturn(Collections.emptyList());

        listingService.getAllListings(ListingSearchQuery.builder().build());

        Document projection = queryCaptor.getValue().getFieldsObject();
        assertEquals(0, projection.get(EMBEDDING_FIELD));
    }

    @Test
    @DisplayName("Should build filter criteria for every supported parameter")
    void testGetAllListings_BuildsFilters() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.find(queryCaptor.capture(), eq(Listing.class)))
                .thenReturn(Collections.emptyList());

        listingService.getAllListings(ListingSearchQuery.builder()
                .propertyType("Apartment")
                .market("Porto")
                .amenity("Wifi")
                .minPrice(new BigDecimal("20"))
                .maxPrice(new BigDecimal("150"))
                .minBedrooms(2)
                .minAccommodates(4)
                .minRating(90)
                .superhostOnly(true)
                .build());

        Document criteria = queryCaptor.getValue().getQueryObject();
        assertTrue(criteria.containsKey("property_type"));
        assertTrue(criteria.containsKey("address.market"));
        assertTrue(criteria.containsKey("amenities"));
        assertTrue(criteria.containsKey("price"));
        assertTrue(criteria.containsKey("bedrooms"));
        assertTrue(criteria.containsKey("accommodates"));
        assertTrue(criteria.containsKey("review_scores.review_scores_rating"));
        assertEquals(true, criteria.get("host.host_is_superhost"));
    }

    @Test
    @DisplayName("Should sort by the mapped MongoDB path, not the API property name")
    void testGetAllListings_TranslatesSortField() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.find(queryCaptor.capture(), eq(Listing.class)))
                .thenReturn(Collections.emptyList());

        listingService.getAllListings(ListingSearchQuery.builder()
                .sortBy("reviewScore")
                .sortOrder("desc")
                .build());

        Document sort = queryCaptor.getValue().getSortObject();
        assertEquals(-1, sort.get("review_scores.review_scores_rating"));
    }

    // ==================== DISTINCT TESTS ====================

    @Test
    @DisplayName("Should drop blank values and sort distinct property types")
    void testGetDistinctPropertyTypes() {
        when(mongoTemplate.findDistinct(any(Query.class), eq("property_type"), eq(COLLECTION), eq(String.class)))
                .thenReturn(Arrays.asList("House", null, "", "Apartment"));

        List<String> result = listingService.getDistinctPropertyTypes();

        assertEquals(List.of("Apartment", "House"), result);
    }

    @Test
    @DisplayName("Should return distinct amenities from the array field")
    void testGetDistinctAmenities() {
        when(mongoTemplate.findDistinct(any(Query.class), eq("amenities"), eq(COLLECTION), eq(String.class)))
                .thenReturn(Arrays.asList("Wifi", "Kitchen"));

        assertEquals(List.of("Kitchen", "Wifi"), listingService.getDistinctAmenities());
    }

    // ==================== GET BY ID TESTS ====================

    @Test
    @DisplayName("Should get a listing by its string id")
    void testGetListingById_Success() {
        when(listingRepository.findById(testId)).thenReturn(Optional.of(testListing));

        Listing result = listingService.getListingById(testId);

        assertNotNull(result);
        assertEquals(testListing.getName(), result.getName());
        verify(listingRepository).findById(testId);
    }

    @Test
    @DisplayName("Should reject a blank id")
    void testGetListingById_BlankId() {
        assertThrows(ValidationException.class, () -> listingService.getListingById("   "));
        verify(listingRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("Should throw when a listing does not exist")
    void testGetListingById_NotFound() {
        when(listingRepository.findById(testId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> listingService.getListingById(testId));
    }

    // ==================== CREATE TESTS ====================

    @Test
    @DisplayName("Should create a listing and assign a string id")
    void testCreateListing_Success() {
        ArgumentCaptor<Listing> listingCaptor = ArgumentCaptor.forClass(Listing.class);
        when(listingRepository.save(listingCaptor.capture())).thenAnswer(call -> call.getArgument(0));

        Listing result = listingService.createListing(createRequest);

        assertNotNull(result);
        assertEquals("Sunny Loft in Kopje", result.getName());
        // Ids must stay strings so that they match the ids already in the dataset
        assertNotNull(listingCaptor.getValue().getId());
        assertEquals(24, listingCaptor.getValue().getId().length());
    }

    @Test
    @DisplayName("Should assemble nested subdocuments from the flat create request")
    void testCreateListing_BuildsNestedDocuments() {
        ArgumentCaptor<Listing> listingCaptor = ArgumentCaptor.forClass(Listing.class);
        when(listingRepository.save(listingCaptor.capture())).thenAnswer(call -> call.getArgument(0));

        listingService.createListing(CreateListingRequest.builder()
                .name("Kopje Cottage")
                .hostName("Rudo")
                .pictureUrl("https://example.com/a.jpg")
                .market("Harare")
                .country("Zimbabwe")
                .longitude(31.0534)
                .latitude(-17.8252)
                .build());

        Listing saved = listingCaptor.getValue();
        assertEquals("Rudo", saved.getHost().getHostName());
        assertEquals("https://example.com/a.jpg", saved.getImages().getPictureUrl());
        assertEquals("Harare", saved.getAddress().getMarket());
        assertEquals("Point", saved.getAddress().getLocation().getType());
        // GeoJSON stores longitude first
        assertEquals(31.0534, saved.getAddress().getLocation().getCoordinates().get(0));
        assertEquals(-17.8252, saved.getAddress().getLocation().getCoordinates().get(1));
    }

    @Test
    @DisplayName("Should reject a create request with a blank name")
    void testCreateListing_BlankName() {
        CreateListingRequest invalid = CreateListingRequest.builder().name("  ").build();

        assertThrows(ValidationException.class, () -> listingService.createListing(invalid));
        verify(listingRepository, never()).save(any(Listing.class));
    }

    @Test
    @DisplayName("Should create listings in batch")
    void testCreateListingsBatch_Success() {
        when(listingRepository.saveAll(any())).thenAnswer(call -> {
            List<Listing> listings = call.getArgument(0);
            return listings;
        });

        BatchInsertResponse result = listingService.createListingsBatch(List.of(createRequest, createRequest));

        assertEquals(2, result.insertedCount());
        assertEquals(2, result.insertedIds().size());
    }

    @Test
    @DisplayName("Should reject an empty batch")
    void testCreateListingsBatch_Empty() {
        assertThrows(ValidationException.class, () -> listingService.createListingsBatch(List.of()));
    }

    @Test
    @DisplayName("Should report the index of an invalid listing in a batch")
    void testCreateListingsBatch_InvalidEntry() {
        CreateListingRequest invalid = CreateListingRequest.builder().name("").build();

        ValidationException exception = assertThrows(ValidationException.class,
                () -> listingService.createListingsBatch(Arrays.asList(createRequest, invalid)));

        assertTrue(exception.getMessage().contains("index 1"));
    }

    // ==================== UPDATE TESTS ====================

    @Test
    @DisplayName("Should translate camelCase properties to MongoDB paths on update")
    void testUpdateListing_TranslatesFieldPaths() {
        UpdateResult updateResult = UpdateResult.acknowledged(1, 1L, null);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        when(mongoTemplate.updateFirst(any(Query.class), updateCaptor.capture(), eq(Listing.class)))
                .thenReturn(updateResult);
        when(listingRepository.findById(testId)).thenReturn(Optional.of(testListing));

        listingService.updateListing(testId, UpdateListingRequest.builder()
                .propertyType("Apartment")
                .neighborhoodOverview("Quiet street")
                .hostName("Rudo")
                .pictureUrl("https://example.com/b.jpg")
                .market("Harare")
                .build());

        Document set = updateCaptor.getValue().getUpdateObject().get("$set", Document.class);
        assertEquals("Apartment", set.get("property_type"));
        assertEquals("Quiet street", set.get("neighborhood_overview"));
        assertEquals("Rudo", set.get("host.host_name"));
        assertEquals("https://example.com/b.jpg", set.get("images.picture_url"));
        assertEquals("Harare", set.get("address.market"));
    }

    @Test
    @DisplayName("Should reject an update with no fields set")
    void testUpdateListing_NoFields() {
        UpdateListingRequest empty = UpdateListingRequest.builder().build();

        assertThrows(ValidationException.class, () -> listingService.updateListing(testId, empty));
    }

    @Test
    @DisplayName("Should throw when updating a listing that does not exist")
    void testUpdateListing_NotFound() {
        UpdateResult updateResult = UpdateResult.acknowledged(0, 0L, null);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Listing.class)))
                .thenReturn(updateResult);

        UpdateListingRequest request = UpdateListingRequest.builder().name("New name").build();

        assertThrows(ResourceNotFoundException.class, () -> listingService.updateListing(testId, request));
    }

    @Test
    @DisplayName("Should translate field paths in batch updates too")
    void testUpdateListingsBatch_TranslatesFieldPaths() {
        UpdateResult updateResult = UpdateResult.acknowledged(3, 3L, null);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);

        when(mongoTemplate.updateMulti(queryCaptor.capture(), updateCaptor.capture(), eq(Listing.class)))
                .thenReturn(updateResult);

        UpdateListingRequest update = UpdateListingRequest.builder()
                .propertyType("Apartment")
                .build();

        BatchUpdateResponse result =
                listingService.updateListingsBatch(List.of("a", "b", "c"), update);

        assertEquals(3, result.matchedCount());
        assertEquals(3, result.modifiedCount());

        Document set = updateCaptor.getValue().getUpdateObject().get("$set", Document.class);
        assertEquals("Apartment", set.get("property_type"));

        // The query must be built from the ids the caller named, never from caller-supplied criteria
        Document queryObject = queryCaptor.getValue().getQueryObject();
        assertEquals(Set.of("_id"), queryObject.keySet());
        assertEquals(List.of("a", "b", "c"), queryObject.get("_id", Document.class).get("$in"));
    }

    @Test
    @DisplayName("Should reject a batch update that changes nothing")
    void testUpdateListingsBatch_EmptyUpdate() {
        assertThrows(ValidationException.class,
                () -> listingService.updateListingsBatch(List.of("a"), UpdateListingRequest.builder().build()));
        assertThrows(ValidationException.class,
                () -> listingService.updateListingsBatch(List.of("a"), null));
    }

    @Test
    @DisplayName("Should reject a batch update with no ids")
    void testUpdateListingsBatch_NoIds() {
        UpdateListingRequest update = UpdateListingRequest.builder().propertyType("Apartment").build();

        assertThrows(ValidationException.class, () -> listingService.updateListingsBatch(List.of(), update));
        assertThrows(ValidationException.class, () -> listingService.updateListingsBatch(null, update));
    }

    // ==================== DELETE TESTS ====================

    @Test
    @DisplayName("Should delete a listing by id")
    void testDeleteListing_Success() {
        when(listingRepository.existsById(testId)).thenReturn(true);

        DeleteResponse result = listingService.deleteListing(testId);

        assertEquals(1L, result.deletedCount());
        verify(listingRepository).deleteById(testId);
    }

    @Test
    @DisplayName("Should throw when deleting a listing that does not exist")
    void testDeleteListing_NotFound() {
        when(listingRepository.existsById(testId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> listingService.deleteListing(testId));
        verify(listingRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("Should refuse to delete a batch with no usable ids")
    void testDeleteListingsBatch_NoIds() {
        assertThrows(ValidationException.class, () -> listingService.deleteListingsBatch(List.of()));
        assertThrows(ValidationException.class, () -> listingService.deleteListingsBatch(null));
        // Blank entries are stripped, so a list of them is as empty as a list of none
        assertThrows(ValidationException.class,
                () -> listingService.deleteListingsBatch(Arrays.asList("  ", null)));

        verify(mongoTemplate, never()).remove(any(Query.class), eq(Listing.class));
    }

    @Test
    @DisplayName("Should delete exactly the listings named by id")
    void testDeleteListingsBatch_Success() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);

        when(mongoTemplate.remove(queryCaptor.capture(), eq(Listing.class)))
                .thenReturn(DeleteResult.acknowledged(4));

        DeleteResponse result = listingService.deleteListingsBatch(List.of("a", "b", "c", "d"));

        assertEquals(4L, result.deletedCount());

        // The whole point of the contract: the query can only ever be an _id match on the named
        // ids, so a caller cannot widen a delete to documents it did not list
        Document queryObject = queryCaptor.getValue().getQueryObject();
        assertEquals(Set.of("_id"), queryObject.keySet());
        assertEquals(List.of("a", "b", "c", "d"), queryObject.get("_id", Document.class).get("$in"));
    }

    @Test
    @DisplayName("Should cap how many listings one batch may target")
    void testDeleteListingsBatch_RejectsOversizedBatch() {
        List<String> tooMany = IntStream.rangeClosed(1, 101)
                .mapToObj(String::valueOf)
                .toList();

        assertThrows(ValidationException.class, () -> listingService.deleteListingsBatch(tooMany));
        verify(mongoTemplate, never()).remove(any(Query.class), eq(Listing.class));
    }

    @Test
    @DisplayName("Should deduplicate ids so a repeated id does not inflate the batch")
    void testDeleteListingsBatch_DeduplicatesIds() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);

        when(mongoTemplate.remove(queryCaptor.capture(), eq(Listing.class)))
                .thenReturn(DeleteResult.acknowledged(1));

        listingService.deleteListingsBatch(List.of("a", "a", " a ", "b"));

        Document queryObject = queryCaptor.getValue().getQueryObject();
        assertEquals(List.of("a", "b"), queryObject.get("_id", Document.class).get("$in"));
    }

    @Test
    @DisplayName("Should throw when find-and-delete matches nothing")
    void testFindAndDeleteListing_NotFound() {
        when(mongoTemplate.findAndRemove(any(Query.class), eq(Listing.class))).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> listingService.findAndDeleteListing(testId));
    }

    // ==================== AGGREGATION TESTS ====================

    @Test
    @DisplayName("Should coerce Decimal128 prices when mapping property type statistics")
    void testGetPropertyTypeStatistics_CoercesNumericTypes() {
        Document row = new Document("_id", "Apartment")
                .append("listingCount", 3626)
                .append("averagePrice", new Decimal128(new BigDecimal("140.5549")))
                .append("highestPrice", 999.0)
                .append("lowestPrice", 10.0)
                .append("averageRating", 93.4999)
                .append("averageAccommodates", 3.44)
                .append("totalReviews", 120000L);

        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(row), new Document()));

        List<PropertyTypeStatisticsResult> results = listingService.getPropertyTypeStatistics(20);

        assertEquals(1, results.size());
        PropertyTypeStatisticsResult stats = results.get(0);
        assertEquals("Apartment", stats.propertyType());
        assertEquals(3626, stats.listingCount());
        assertEquals(140.55, stats.averagePrice());
        assertEquals(93.5, stats.averageRating());
        assertEquals(3.4, stats.averageAccommodates());
        assertEquals(120000L, stats.totalReviews());
    }

    @Test
    @DisplayName("Should map amenity statistics")
    void testGetAmenityStatistics() {
        Document row = new Document("_id", "Wifi")
                .append("listingCount", 5000)
                .append("averagePrice", 120.456)
                .append("averageRating", 92.1);

        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(row), new Document()));

        List<AmenityStatisticsResult> results = listingService.getAmenityStatistics(20);

        assertEquals("Wifi", results.get(0).amenity());
        assertEquals(5000, results.get(0).listingCount());
        assertEquals(120.46, results.get(0).averagePrice());
    }

    // ==================== GEOSPATIAL TESTS ====================

    @Test
    @DisplayName("Should require both coordinates for proximity search")
    void testFindNearbyListings_MissingCoordinates() {
        assertThrows(ValidationException.class,
                () -> listingService.findNearbyListings(null, 41.14, 2000, 10));
        assertThrows(ValidationException.class,
                () -> listingService.findNearbyListings(-8.61, null, 2000, 10));
    }

    @Test
    @DisplayName("Should reject out-of-range coordinates")
    void testFindNearbyListings_InvalidCoordinates() {
        assertThrows(ValidationException.class,
                () -> listingService.findNearbyListings(-200.0, 41.14, 2000, 10));
        assertThrows(ValidationException.class,
                () -> listingService.findNearbyListings(-8.61, 120.0, 2000, 10));
    }

    // ==================== SEARCH TESTS ====================

    @Test
    @DisplayName("Should require at least one search field")
    void testSearchListings_NoFields() {
        ListingSearchRequest request = ListingSearchRequest.builder().build();

        assertThrows(ValidationException.class, () -> listingService.searchListings(request));
    }

    @Test
    @DisplayName("Should reject an unsupported compound operator")
    void testSearchListings_InvalidOperator() {
        ListingSearchRequest request = ListingSearchRequest.builder()
                .summary("river view")
                .searchOperator("maybe")
                .build();

        assertThrows(ValidationException.class, () -> listingService.searchListings(request));
    }

    // ==================== VECTOR SEARCH TESTS ====================

    @Test
    @DisplayName("Should require a query for vector search")
    void testVectorSearch_BlankQuery() {
        assertThrows(ValidationException.class, () -> listingService.vectorSearchListings("  ", 10));
    }

    @Test
    @DisplayName("Should report a missing Voyage API key as a service configuration problem")
    void testVectorSearch_MissingApiKey() {
        ReflectionTestUtils.setField(listingService, "voyageApiKey", null);

        assertThrows(ServiceUnavailableException.class,
                () -> listingService.vectorSearchListings("quiet cabin near the beach", 10));
    }

    @Test
    @DisplayName("Should treat the placeholder API key as unconfigured")
    void testVectorSearch_PlaceholderApiKey() {
        ReflectionTestUtils.setField(listingService, "voyageApiKey", "your_voyage_api_key");

        assertThrows(ServiceUnavailableException.class,
                () -> listingService.vectorSearchListings("quiet cabin", 10));
    }

    @Test
    @DisplayName("Should require an API key before backfilling embeddings")
    void testBackfill_MissingApiKey() {
        ReflectionTestUtils.setField(listingService, "voyageApiKey", "");

        assertThrows(ServiceUnavailableException.class,
                () -> listingService.backfillDescriptionEmbeddings(50));
    }

    // ==================== VOYAGE RETRY TESTS ====================

    /** A 429 with no Retry-After header, which is what the backoff path has to handle. */
    @SuppressWarnings("unchecked")
    private static HttpResponse<String> rateLimited() {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        return response;
    }

    private static HttpRequest anyRequest() {
        return HttpRequest.newBuilder().uri(URI.create("https://example.com/embeddings")).build();
    }

    @Test
    @DisplayName("Should retry a rate-limited Voyage request and return the eventual success")
    void testSendWithRetry_RetriesOnRateLimit() throws Exception {
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(listingService, "httpClient", http);
        // Zero so the test does not sit through the real backoff
        ReflectionTestUtils.setField(listingService, "retryInitialDelayMs", 0L);

        @SuppressWarnings("unchecked")
        HttpResponse<String> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);

        // Built before the stubbing call: rateLimited() stubs its own mock, and Mockito rejects
        // that happening inside an open when(...) chain
        HttpResponse<String> firstLimit = rateLimited();
        HttpResponse<String> secondLimit = rateLimited();

        when(http.<String>send(any(HttpRequest.class), any()))
                .thenReturn(firstLimit, secondLimit, ok);

        HttpResponse<String> result = listingService.sendWithRetry(anyRequest());

        assertEquals(200, result.statusCode());
        verify(http, times(3)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("Should give up after the attempt limit and hand the last response back")
    void testSendWithRetry_StopsAfterAttemptLimit() throws Exception {
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(listingService, "httpClient", http);
        ReflectionTestUtils.setField(listingService, "retryInitialDelayMs", 0L);

        HttpResponse<String> limited = rateLimited();
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(limited);

        HttpResponse<String> result = listingService.sendWithRetry(anyRequest());

        // Returned rather than thrown, so the caller still raises VoyageAPIException carrying the
        // real status rather than a generic retry failure
        assertEquals(429, result.statusCode());
        verify(http, times(4)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("Should not retry a failure that will not change, such as a bad API key")
    void testSendWithRetry_DoesNotRetryUnauthorised() throws Exception {
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(listingService, "httpClient", http);
        ReflectionTestUtils.setField(listingService, "retryInitialDelayMs", 0L);

        @SuppressWarnings("unchecked")
        HttpResponse<String> unauthorised = mock(HttpResponse.class);
        when(unauthorised.statusCode()).thenReturn(401);

        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(unauthorised);

        HttpResponse<String> result = listingService.sendWithRetry(anyRequest());

        assertEquals(401, result.statusCode());
        verify(http, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("Should retry a dropped connection as readily as a rate limit")
    void testSendWithRetry_RetriesNetworkFailure() throws Exception {
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(listingService, "httpClient", http);
        ReflectionTestUtils.setField(listingService, "retryInitialDelayMs", 0L);

        @SuppressWarnings("unchecked")
        HttpResponse<String> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);

        when(http.<String>send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("connection reset"))
                .thenReturn(ok);

        HttpResponse<String> result = listingService.sendWithRetry(anyRequest());

        assertEquals(200, result.statusCode());
        verify(http, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("A network failure that never clears should surface rather than be swallowed")
    void testSendWithRetry_ThrowsWhenNetworkNeverRecovers() throws Exception {
        HttpClient http = mock(HttpClient.class);
        ReflectionTestUtils.setField(listingService, "httpClient", http);
        ReflectionTestUtils.setField(listingService, "retryInitialDelayMs", 0L);

        when(http.<String>send(any(HttpRequest.class), any())).thenThrow(new IOException("connection reset"));

        assertThrows(IOException.class, () -> listingService.sendWithRetry(anyRequest()));
        verify(http, times(4)).send(any(HttpRequest.class), any());
    }
}
