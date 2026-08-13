package com.kumusha.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.kumusha.model.dto.EmbeddingBackfillResponse;
import com.kumusha.model.dto.ListingFacetsResult;
import com.kumusha.model.dto.ListingSearchQuery;
import com.kumusha.model.dto.ListingSearchRequest;
import com.kumusha.model.dto.ListingWithReviewsResult;
import com.kumusha.model.dto.NearbyListingResult;
import com.kumusha.model.dto.PropertyTypeStatisticsResult;
import com.kumusha.model.dto.UpdateListingRequest;
import com.kumusha.model.dto.VectorSearchResult;
import com.kumusha.service.ListingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for ListingControllerImpl.
 *
 * <p>These tests verify the REST API endpoints by mocking the service layer.
 * They use Spring's MockMvc to exercise HTTP requests and responses, including the
 * status codes and error envelopes produced by GlobalExceptionHandler.
 *
 * <p>Writes are enabled here so these tests can exercise the create, update and delete endpoints
 * on their own terms. The read-only guard that protects those endpoints by default is covered
 * separately by {@link WriteGuardTest}.
 */
@WebMvcTest(ListingControllerImpl.class)
@TestPropertySource(properties = "kumusha.write.enabled=true")
@DisplayName("ListingController Unit Tests")
class ListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ListingService listingService;

    private String testId;
    private Listing testListing;
    private CreateListingRequest createRequest;
    private UpdateListingRequest updateRequest;

    @BeforeEach
    void setUp() {
        testId = "10006546";

        testListing = Listing.builder()
                .id(testId)
                .name("Ribeira Charming Duplex")
                .propertyType("House")
                .roomType("Entire home/apt")
                .accommodates(8)
                .bedrooms(3)
                .price(new BigDecimal("80.00"))
                .amenities(List.of("Wifi", "Kitchen"))
                .build();

        createRequest = CreateListingRequest.builder()
                .name("Sunny Loft in Kopje")
                .propertyType("Loft")
                .accommodates(2)
                .price(new BigDecimal("45.00"))
                .build();

        updateRequest = UpdateListingRequest.builder()
                .name("Updated Name")
                .price(new BigDecimal("99.00"))
                .build();
    }

    // ==================== GET ALL LISTINGS TESTS ====================

    @Test
    @DisplayName("GET /api/listings - Should return list of listings")
    void testGetAllListings_Success() throws Exception {
        when(listingService.getAllListings(any(ListingSearchQuery.class))).thenReturn(List.of(testListing));

        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Ribeira Charming Duplex"))
                .andExpect(jsonPath("$.data[0]._id").value(testId));
    }

    @Test
    @DisplayName("GET /api/listings - Should handle query parameters")
    void testGetAllListings_WithQueryParams() throws Exception {
        when(listingService.getAllListings(any(ListingSearchQuery.class))).thenReturn(List.of(testListing));

        mockMvc.perform(get("/api/listings")
                        .param("q", "duplex")
                        .param("propertyType", "House")
                        .param("market", "Porto")
                        .param("minPrice", "20")
                        .param("maxPrice", "150")
                        .param("minAccommodates", "4")
                        .param("superhostOnly", "true")
                        .param("limit", "10")
                        .param("skip", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ==================== FACET TESTS ====================

    @Test
    @DisplayName("GET /api/listings/property-types - Should return distinct property types")
    void testGetDistinctPropertyTypes() throws Exception {
        when(listingService.getDistinctPropertyTypes()).thenReturn(List.of("Apartment", "House"));

        mockMvc.perform(get("/api/listings/property-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0]").value("Apartment"));
    }

    @Test
    @DisplayName("GET /api/listings/amenities - Should return distinct amenities")
    void testGetDistinctAmenities() throws Exception {
        when(listingService.getDistinctAmenities()).thenReturn(List.of("Kitchen", "Wifi"));

        mockMvc.perform(get("/api/listings/amenities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/listings/facets - Should return filter facets")
    void testGetFacets() throws Exception {
        when(listingService.getListingFacets()).thenReturn(ListingFacetsResult.builder()
                .propertyTypes(List.of("Apartment"))
                .roomTypes(List.of("Entire home/apt"))
                .markets(List.of("Porto"))
                .minPrice(10.0)
                .maxPrice(999.0)
                .build());

        mockMvc.perform(get("/api/listings/facets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.propertyTypes", hasSize(1)))
                .andExpect(jsonPath("$.data.markets[0]").value("Porto"))
                .andExpect(jsonPath("$.data.maxPrice").value(999.0));
    }

    // ==================== GET LISTING BY ID TESTS ====================

    @Test
    @DisplayName("GET /api/listings/{id} - Should return listing by id")
    void testGetListingById_Success() throws Exception {
        when(listingService.getListingById(testId)).thenReturn(testListing);

        mockMvc.perform(get("/api/listings/{id}", testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Ribeira Charming Duplex"))
                .andExpect(jsonPath("$.data.accommodates").value(8));
    }

    @Test
    @DisplayName("GET /api/listings/{id} - Should return 404 when listing not found")
    void testGetListingById_NotFound() throws Exception {
        when(listingService.getListingById(testId))
                .thenThrow(new ResourceNotFoundException("Listing not found"));

        mockMvc.perform(get("/api/listings/{id}", testId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Listing not found"));
    }

    @Test
    @DisplayName("GET /api/listings/{id} - Should return 400 for a blank id")
    void testGetListingById_InvalidId() throws Exception {
        when(listingService.getListingById("   "))
                .thenThrow(new ValidationException("Listing ID is required"));

        mockMvc.perform(get("/api/listings/{id}", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ==================== CREATE TESTS ====================

    @Test
    @DisplayName("POST /api/listings - Should create listing successfully")
    void testCreateListing_Success() throws Exception {
        when(listingService.createListing(any(CreateListingRequest.class))).thenReturn(testListing);

        mockMvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Ribeira Charming Duplex"));
    }

    @Test
    @DisplayName("POST /api/listings - Should return 400 when name is blank")
    void testCreateListing_BlankName() throws Exception {
        CreateListingRequest invalid = CreateListingRequest.builder().name("  ").build();

        mockMvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/listings/batch - Should create listings in batch")
    void testCreateListingsBatch_Success() throws Exception {
        when(listingService.createListingsBatch(any()))
                .thenReturn(new BatchInsertResponse(2, List.of("a1", "b2")));

        mockMvc.perform(post("/api/listings/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(createRequest, createRequest))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insertedCount").value(2))
                .andExpect(jsonPath("$.data.insertedIds", hasSize(2)));
    }

    // ==================== UPDATE TESTS ====================

    @Test
    @DisplayName("PATCH /api/listings/{id} - Should update listing")
    void testUpdateListing_Success() throws Exception {
        when(listingService.updateListing(eq(testId), any(UpdateListingRequest.class))).thenReturn(testListing);

        mockMvc.perform(patch("/api/listings/{id}", testId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data._id").value(testId));
    }

    @Test
    @DisplayName("PATCH /api/listings - Should update listings in batch")
    void testUpdateListingsBatch_Success() throws Exception {
        when(listingService.updateListingsBatch(anyList(), any(UpdateListingRequest.class)))
                .thenReturn(new BatchUpdateResponse(3, 3));

        Map<String, Object> body = new HashMap<>();
        body.put("ids", List.of("a", "b", "c"));
        body.put("update", Map.of("propertyType", "Apartment"));

        mockMvc.perform(patch("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matchedCount").value(3))
                .andExpect(jsonPath("$.data.modifiedCount").value(3));
    }

    // ==================== DELETE TESTS ====================

    @Test
    @DisplayName("DELETE /api/listings/{id} - Should delete listing")
    void testDeleteListing_Success() throws Exception {
        when(listingService.deleteListing(testId)).thenReturn(new DeleteResponse(1L));

        mockMvc.perform(delete("/api/listings/{id}", testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deletedCount").value(1));
    }

    @Test
    @DisplayName("DELETE /api/listings/{id}/find-and-delete - Should return the deleted listing")
    void testFindAndDeleteListing_Success() throws Exception {
        when(listingService.findAndDeleteListing(testId)).thenReturn(testListing);

        mockMvc.perform(delete("/api/listings/{id}/find-and-delete", testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Ribeira Charming Duplex"));
    }

    @Test
    @DisplayName("DELETE /api/listings - Should reject a batch with no ids")
    void testDeleteListingsBatch_NoIds() throws Exception {
        when(listingService.deleteListingsBatch(any()))
                .thenThrow(new ValidationException("At least one listing id is required"));

        mockMvc.perform(delete("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("DELETE /api/listings - Should pass the named ids through to the service")
    void testDeleteListingsBatch_Success() throws Exception {
        when(listingService.deleteListingsBatch(anyList())).thenReturn(new DeleteResponse(2L));

        mockMvc.perform(delete("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [\"a\", \"b\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deletedCount").value(2));

        verify(listingService).deleteListingsBatch(List.of("a", "b"));
    }

    // ==================== AGGREGATION TESTS ====================

    @Test
    @DisplayName("GET /api/listings/aggregations/reportingByReviews - Should return recently reviewed listings")
    void testReportingByReviews() throws Exception {
        ListingWithReviewsResult result = ListingWithReviewsResult.builder()
                ._id(testId)
                .name("Ribeira Charming Duplex")
                .totalReviews(51)
                .mostRecentReviewDate(Instant.parse("2019-01-20T05:00:00Z"))
                .recentReviews(List.of(ListingWithReviewsResult.ReviewInfo.builder()
                        .id("r1")
                        .reviewerName("Tendai")
                        .comments("Lovely place")
                        .date(Instant.parse("2019-01-20T05:00:00Z"))
                        .build()))
                .build();

        when(listingService.getListingsWithRecentReviews(anyInt(), isNull())).thenReturn(List.of(result));

        mockMvc.perform(get("/api/listings/aggregations/reportingByReviews").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].totalReviews").value(51))
                .andExpect(jsonPath("$.data[0].recentReviews[0].reviewerName").value("Tendai"));
    }

    @Test
    @DisplayName("GET /api/listings/aggregations/reportingByPropertyType - Should return property type statistics")
    void testReportingByPropertyType() throws Exception {
        when(listingService.getPropertyTypeStatistics(anyInt())).thenReturn(List.of(
                PropertyTypeStatisticsResult.builder()
                        .propertyType("Apartment")
                        .listingCount(3626)
                        .averagePrice(140.55)
                        .averageRating(93.5)
                        .build()));

        mockMvc.perform(get("/api/listings/aggregations/reportingByPropertyType"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].propertyType").value("Apartment"))
                .andExpect(jsonPath("$.data[0].listingCount").value(3626))
                .andExpect(jsonPath("$.data[0].averagePrice").value(140.55));
    }

    @Test
    @DisplayName("GET /api/listings/aggregations/reportingByAmenities - Should return amenity statistics")
    void testReportingByAmenities() throws Exception {
        when(listingService.getAmenityStatistics(anyInt())).thenReturn(List.of(
                AmenityStatisticsResult.builder()
                        .amenity("Wifi")
                        .listingCount(5000)
                        .averagePrice(120.0)
                        .build()));

        mockMvc.perform(get("/api/listings/aggregations/reportingByAmenities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].amenity").value("Wifi"))
                .andExpect(jsonPath("$.data[0].listingCount").value(5000));
    }

    // ==================== GEOSPATIAL TESTS ====================

    @Test
    @DisplayName("GET /api/listings/nearby - Should return listings ordered by distance")
    void testNearby_Success() throws Exception {
        when(listingService.findNearbyListings(any(), any(), anyInt(), anyInt())).thenReturn(List.of(
                NearbyListingResult.builder()
                        ._id(testId)
                        .name("Ribeira Charming Duplex")
                        .distanceMeters(412.3)
                        .build()));

        mockMvc.perform(get("/api/listings/nearby")
                        .param("longitude", "-8.61308")
                        .param("latitude", "41.1413")
                        .param("maxDistanceMeters", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].distanceMeters").value(412.3));
    }

    @Test
    @DisplayName("GET /api/listings/nearby - Should return 400 when coordinates are missing")
    void testNearby_MissingParams() throws Exception {
        mockMvc.perform(get("/api/listings/nearby").param("longitude", "-8.61308"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ==================== SEARCH TESTS ====================

    @Test
    @DisplayName("GET /api/listings/search - Should return search results")
    void testSearch_Success() throws Exception {
        when(listingService.searchListings(any(ListingSearchRequest.class))).thenReturn(List.of(testListing));

        mockMvc.perform(get("/api/listings/search").param("summary", "river view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.listings", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/listings/search - Should return 400 when no search field is provided")
    void testSearch_NoFields() throws Exception {
        when(listingService.searchListings(any(ListingSearchRequest.class)))
                .thenThrow(new ValidationException("At least one search parameter must be provided"));

        mockMvc.perform(get("/api/listings/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ==================== VECTOR SEARCH TESTS ====================

    @Test
    @DisplayName("GET /api/listings/vector-search - Should return scored results")
    void testVectorSearch_Success() throws Exception {
        when(listingService.vectorSearchListings(anyString(), anyInt())).thenReturn(List.of(
                VectorSearchResult.builder()
                        .id(testId)
                        .name("Ribeira Charming Duplex")
                        .score(0.87)
                        .build()));

        mockMvc.perform(get("/api/listings/vector-search").param("q", "quiet place near the river"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].score").value(0.87));
    }

    @Test
    @DisplayName("GET /api/listings/vector-search - Should surface a missing API key as a 400")
    void testVectorSearch_NoApiKey() throws Exception {
        when(listingService.vectorSearchListings(anyString(), anyInt()))
                .thenThrow(new ServiceUnavailableException("Vector search unavailable: VOYAGE_API_KEY not configured"));

        mockMvc.perform(get("/api/listings/vector-search").param("q", "cabin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("GET /api/listings/find-similar-listings - Should return neighbours")
    void testFindSimilar_Success() throws Exception {
        when(listingService.findSimilarListings(anyString(), anyInt())).thenReturn(List.of(testListing));

        mockMvc.perform(get("/api/listings/find-similar-listings").param("listingId", testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("POST /api/listings/embeddings/backfill - Should report progress")
    void testBackfill_Success() throws Exception {
        when(listingService.backfillDescriptionEmbeddings(anyInt())).thenReturn(
                EmbeddingBackfillResponse.builder()
                        .embeddedCount(50)
                        .skippedCount(0)
                        .remainingCount(5505)
                        .embeddingField("description_embedding")
                        .dimensions(2048)
                        .build());

        mockMvc.perform(post("/api/listings/embeddings/backfill").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.embeddedCount").value(50))
                .andExpect(jsonPath("$.data.remainingCount").value(5505))
                .andExpect(jsonPath("$.data.dimensions").value(2048));
    }
}
