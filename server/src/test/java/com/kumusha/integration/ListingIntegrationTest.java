package com.kumusha.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kumusha.model.dto.AmenityStatisticsResult;
import com.kumusha.model.dto.BatchUpdateResponse;
import com.kumusha.model.dto.DeleteResponse;
import com.kumusha.model.dto.ListingFacetsResult;
import com.kumusha.model.dto.ListingSearchQuery;
import com.kumusha.model.dto.ListingWithReviewsResult;
import com.kumusha.model.dto.ListingsPageResponse;
import com.kumusha.model.dto.NearbyListingResult;
import com.kumusha.model.dto.PropertyTypeStatisticsResult;
import com.kumusha.model.dto.UpdateListingRequest;
import com.kumusha.service.ListingService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the service against a real MongoDB rather than a mocked MongoTemplate.
 *
 * <p>The rest of the suite stubs {@code mongoTemplate}, which means the aggregation pipelines are
 * never executed: a test can assert the shape of a hand-built result document while the pipeline
 * that was supposed to produce it goes uninspected. Everything here runs the real query, so a
 * malformed stage, a mistyped field path or a missing index fails the test.
 *
 * <p>Documents are seeded as raw BSON rather than through the entity mapper, so the stored shape
 * matches the {@code sample_airbnb} dataset exactly: prices as {@code Decimal128}, addresses
 * carrying GeoJSON points, and reviews embedded in the listing itself.
 *
 * <p>MongoDB Search and Vector Search are deliberately absent. Both are Atlas features that a
 * containerised deployment cannot provide, so they remain covered by the mocked tests only.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Listing Integration Tests (real MongoDB)")
class ListingIntegrationTest {

    private static final String COLLECTION = "listingsAndReviews";

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired
    private ListingService listingService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void seed() {
        mongoTemplate.getCollection(COLLECTION).deleteMany(new Document());
        mongoTemplate.getCollection(COLLECTION).insertMany(List.of(
                listing("1", "Ribeira Duplex", "House", "Entire home/apt", "Porto", "Portugal",
                        "80.00", 8, 3, 89, 51, List.of("Wifi", "Kitchen"), -8.61, 41.14, 3),
                listing("2", "Sea View Loft", "Loft", "Private room", "Barcelona", "Spain",
                        "45.50", 2, 1, 95, 12, List.of("Wifi", "Pool"), 2.17, 41.38, 2),
                listing("3", "Alfama Studio", "Apartment", "Entire home/apt", "Lisbon", "Portugal",
                        "120.00", 4, 2, 91, 30, List.of("Wifi", "Kitchen", "Pool"), -9.13, 38.71, 1),
                listing("4", "Cais Attic", "Apartment", "Entire home/apt", "Porto", "Portugal",
                        "60.00", 3, 1, 78, 5, List.of("Kitchen"), -8.62, 41.15, 0)
        ));
    }

    /** Builds one document in the shape the sample_airbnb collection stores. */
    private static Document listing(String id, String name, String propertyType, String roomType,
                                    String market, String country, String price, int accommodates,
                                    int bedrooms, int rating, int numberOfReviews,
                                    List<String> amenities, double lon, double lat,
                                    int reviewCount) {

        List<Document> reviews = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        Date lastReview = null;

        for (int i = 0; i < reviewCount; i++) {
            calendar.set(2024, Calendar.JANUARY, 1 + i, 12, 0, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date date = calendar.getTime();
            lastReview = date;
            reviews.add(new Document("_id", id + "-r" + i)
                    .append("reviewer_name", "Guest " + i)
                    .append("comments", "Review " + i + " for " + name)
                    .append("date", date));
        }

        Document doc = new Document("_id", id)
                .append("name", name)
                .append("property_type", propertyType)
                .append("room_type", roomType)
                .append("accommodates", accommodates)
                .append("bedrooms", bedrooms)
                .append("number_of_reviews", numberOfReviews)
                .append("amenities", amenities)
                .append("price", new Decimal128(new BigDecimal(price)))
                .append("images", new Document("picture_url", "https://example.com/" + id + ".jpg"))
                .append("host", new Document("host_name", "Host " + id).append("host_is_superhost", true))
                .append("review_scores", new Document("review_scores_rating", rating))
                .append("address", new Document("market", market)
                        .append("country", country)
                        .append("location", new Document("type", "Point")
                                .append("coordinates", List.of(lon, lat))))
                .append("reviews", reviews);

        if (lastReview != null) {
            doc.append("last_review", lastReview);
        }
        return doc;
    }

    // ==================== INDEXES ====================

    @Test
    @DisplayName("Startup should create the indexes the listing queries depend on")
    void createsSupportingIndexes() {
        List<String> names = new ArrayList<>();
        mongoTemplate.getCollection(COLLECTION).listIndexes()
                .forEach(index -> names.add(index.getString("name")));

        // Named explicitly rather than counted, so renaming a field constant fails here instead
        // of silently turning an indexed query back into a collection scan
        assertTrue(names.containsAll(List.of(
                "name_index", "price_index", "review_score_index", "accommodates_index",
                "number_of_reviews_index", "room_type_index", "market_index", "country_index",
                "amenities_index", "bedrooms_index", "market_name_index",
                "property_type_name_index", "location_2dsphere_index")),
                "missing indexes, found: " + names);
    }

    @Test
    @DisplayName("The default listing query should be served by an index, not sorted in memory")
    void defaultQueryUsesAnIndex() {
        Document explain = mongoTemplate.getCollection(COLLECTION)
                .find()
                .sort(new Document("name", 1))
                .limit(20)
                .explain();

        String plan = explain.toJson();

        // An in-memory sort appears as a SORT stage above a COLLSCAN. Its absence is the whole
        // point of the name index, since name is the default sort on every request.
        assertTrue(plan.contains("IXSCAN"), "expected an index scan, plan was: " + plan);
        assertTrue(plan.contains("name_index"), "expected name_index to be used, plan was: " + plan);
    }

    // ==================== QUERY, FILTER, SORT, COUNT ====================

    @Test
    @DisplayName("Should filter, sort and report a total that ignores paging")
    void filtersSortsAndCounts() {
        ListingsPageResponse page = listingService.getAllListings(ListingSearchQuery.builder()
                .market("Porto")
                .sortBy("price")
                .sortOrder("asc")
                .limit(1)
                .build());

        assertEquals(2, page.totalCount(), "total must describe the whole match, not the page");
        assertEquals(1, page.listings().size(), "page must honour the limit");
        assertEquals("Cais Attic", page.listings().get(0).getName(), "cheapest Porto stay first");
    }

    @Test
    @DisplayName("Should match an amenity inside the array field")
    void filtersByAmenity() {
        ListingsPageResponse page = listingService.getAllListings(
                ListingSearchQuery.builder().amenity("Pool").build());

        assertEquals(2, page.totalCount());
    }

    @Test
    @DisplayName("Should apply price and rating bounds together")
    void filtersByPriceAndRating() {
        ListingsPageResponse page = listingService.getAllListings(ListingSearchQuery.builder()
                .minPrice(new BigDecimal("50"))
                .maxPrice(new BigDecimal("130"))
                .minRating(85)
                .build());

        // Ribeira at 80/89 and Alfama at 120/91 qualify; Cais is cheap but rated 78, Sea View
        // is rated 95 but priced below the floor
        assertEquals(2, page.totalCount());
    }

    @Test
    @DisplayName("Should match a market case-insensitively without treating input as a pattern")
    void filtersMarketCaseInsensitively() {
        ListingsPageResponse page = listingService.getAllListings(
                ListingSearchQuery.builder().market("porto").build());

        assertEquals(2, page.totalCount());
    }

    // ==================== AGGREGATIONS ====================

    @Test
    @DisplayName("Facets should come back from the real distinct and price aggregation")
    void buildsFacets() {
        ListingFacetsResult facets = listingService.getListingFacets();

        assertEquals(List.of("Apartment", "House", "Loft"), facets.propertyTypes());
        assertEquals(List.of("Entire home/apt", "Private room"), facets.roomTypes());
        assertEquals(List.of("Barcelona", "Lisbon", "Porto"), facets.markets());
        assertEquals(45.5, facets.minPrice());
        assertEquals(120.0, facets.maxPrice());
    }

    @Test
    @DisplayName("The reviews pipeline should unwind, regroup and slice for real")
    void aggregatesRecentReviews() {
        List<ListingWithReviewsResult> results = listingService.getListingsWithRecentReviews(10, null);

        // Only the three listings that actually carry reviews
        assertEquals(3, results.size());

        ListingWithReviewsResult first = results.get(0);
        assertNotNull(first.mostRecentReviewDate());
        assertNotNull(first.recentReviews());
        assertTrue(first.totalReviews() > 0);

        // $group does not preserve order, so the pipeline sorts again afterwards. That trailing
        // stage is what this asserts.
        assertTrue(!results.get(0).mostRecentReviewDate().isBefore(results.get(1).mostRecentReviewDate()),
                "results must be ordered by review recency, newest first");
    }

    @Test
    @DisplayName("Restricting the reviews report to one listing should return only that listing")
    void aggregatesRecentReviewsForOneListing() {
        List<ListingWithReviewsResult> results = listingService.getListingsWithRecentReviews(10, "1");

        assertEquals(1, results.size());
        assertEquals("Ribeira Duplex", results.get(0).name());
        assertEquals(3, results.get(0).totalReviews());
    }

    @Test
    @DisplayName("Property type statistics should convert Decimal128 prices before averaging")
    void aggregatesPropertyTypeStatistics() {
        List<PropertyTypeStatisticsResult> stats = listingService.getPropertyTypeStatistics(20);

        assertEquals(3, stats.size());

        PropertyTypeStatisticsResult apartments = stats.stream()
                .filter(s -> "Apartment".equals(s.propertyType()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, apartments.listingCount());
        // (120.00 + 60.00) / 2, which only comes out numeric if the Decimal128 conversion ran
        assertEquals(90.0, apartments.averagePrice());
        assertEquals(120.0, apartments.highestPrice());
        assertEquals(60.0, apartments.lowestPrice());
    }

    @Test
    @DisplayName("Amenity statistics should unwind the array and count per amenity")
    void aggregatesAmenityStatistics() {
        List<AmenityStatisticsResult> stats = listingService.getAmenityStatistics(20);

        AmenityStatisticsResult wifi = stats.stream()
                .filter(s -> "Wifi".equals(s.amenity()))
                .findFirst()
                .orElseThrow();
        assertEquals(3, wifi.listingCount());

        AmenityStatisticsResult kitchen = stats.stream()
                .filter(s -> "Kitchen".equals(s.amenity()))
                .findFirst()
                .orElseThrow();
        assertEquals(3, kitchen.listingCount());
    }

    // ==================== GEOSPATIAL ====================

    @Test
    @DisplayName("Proximity search should run $geoNear against the 2dsphere index")
    void findsNearbyListings() {
        // Central Porto: both Porto listings sit within a couple of kilometres
        List<NearbyListingResult> nearby = listingService.findNearbyListings(-8.61, 41.14, 5000, 20);

        assertEquals(2, nearby.size());
        assertEquals("Ribeira Duplex", nearby.get(0).name(), "nearest first");
        assertNotNull(nearby.get(0).distanceMeters());
        assertTrue(nearby.get(0).distanceMeters() <= nearby.get(1).distanceMeters(),
                "$geoNear must return results ordered by distance");
    }

    @Test
    @DisplayName("Proximity search should exclude anything beyond the radius")
    void respectsProximityRadius() {
        List<NearbyListingResult> nearby = listingService.findNearbyListings(-8.61, 41.14, 100, 20);

        assertEquals(1, nearby.size());
        assertEquals("Ribeira Duplex", nearby.get(0).name());
    }

    // ==================== BATCH WRITES ====================

    @Test
    @DisplayName("A batch update should touch only the listings it names")
    void batchUpdateTargetsNamedIdsOnly() {
        BatchUpdateResponse result = listingService.updateListingsBatch(
                List.of("1", "2"),
                UpdateListingRequest.builder().propertyType("Cabin").build());

        assertEquals(2, result.matchedCount());
        assertEquals(2, result.modifiedCount());

        // The camelCase property must have landed on the stored snake_case path
        long cabins = mongoTemplate.getCollection(COLLECTION)
                .countDocuments(new Document("property_type", "Cabin"));
        assertEquals(2, cabins);
    }

    @Test
    @DisplayName("A batch delete should remove only the listings it names")
    void batchDeleteTargetsNamedIdsOnly() {
        DeleteResponse result = listingService.deleteListingsBatch(List.of("1", "3"));

        assertEquals(2L, result.deletedCount());
        assertEquals(2L, mongoTemplate.getCollection(COLLECTION).countDocuments(new Document()));
    }
}
