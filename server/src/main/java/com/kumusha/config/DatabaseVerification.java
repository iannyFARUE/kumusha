package com.kumusha.config;

import com.kumusha.model.Listing;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Database verification component that runs on application startup.
 *
 * <p>This component performs pre-flight checks to ensure the MongoDB database is properly
 * configured and contains the expected data and indexes.
 *
 * <p>Verification steps:
 * <ol>
 *   <li>Check the listingsAndReviews collection exists and contains documents</li>
 *   <li>Create a text search index on name, summary and description</li>
 *   <li>Create a 2dsphere index on address.location for proximity search</li>
 *   <li>Create supporting indexes for the aggregation and filter endpoints</li>
 *   <li>Create the MongoDB Search index used by the /search endpoint</li>
 *   <li>Create the vector search index, once at least one listing has an embedding</li>
 * </ol>
 *
 * <p>Verification is non-blocking: the application starts even if a step fails, but warnings
 * are logged to help identify configuration issues. Index creation commands that require
 * MongoDB Atlas are expected to fail on a plain local deployment, and are logged as warnings
 * rather than errors.
 */
@Component
public class DatabaseVerification {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseVerification.class);

    private static final String LISTINGS_COLLECTION = "listingsAndReviews";
    private static final String TEXT_INDEX_NAME = "text_search_index";
    private static final String LOCATION_INDEX_NAME = "location_2dsphere_index";
    private static final String PROPERTY_TYPE_INDEX_NAME = "property_type_index";
    private static final String LAST_REVIEW_INDEX_NAME = "last_review_index";
    private static final String NAME_INDEX_NAME = "name_index";
    private static final String PRICE_INDEX_NAME = "price_index";
    private static final String REVIEW_SCORE_INDEX_NAME = "review_score_index";
    private static final String NUMBER_OF_REVIEWS_INDEX_NAME = "number_of_reviews_index";
    private static final String ACCOMMODATES_INDEX_NAME = "accommodates_index";
    private static final String ROOM_TYPE_INDEX_NAME = "room_type_index";
    private static final String MARKET_INDEX_NAME = "market_index";
    private static final String COUNTRY_INDEX_NAME = "country_index";
    private static final String AMENITIES_INDEX_NAME = "amenities_index";
    private static final String BEDROOMS_INDEX_NAME = "bedrooms_index";
    private static final String MARKET_NAME_INDEX_NAME = "market_name_index";
    private static final String PROPERTY_TYPE_NAME_INDEX_NAME = "property_type_name_index";
    private static final String MONGODB_SEARCH_INDEX_NAME = "listingSearchIndex";
    private static final String VECTOR_INDEX_NAME = "vector_index";

    private final MongoDatabase database;

    @Value("${kumusha.embedding.field}")
    private String embeddingField;

    @Value("${kumusha.embedding.dimensions}")
    private int embeddingDimensions;

    public DatabaseVerification(MongoDatabase database) {
        this.database = database;
    }

    /**
     * Runs database verification checks after the bean is constructed.
     *
     * <p>This method is called automatically by Spring after dependency injection is complete.
     * It catches all exceptions to prevent application startup failure, but logs errors to help
     * developers identify issues.
     */
    @PostConstruct
    public void verifyDatabase() {
        logger.info("Starting database verification for '{}'...", database.getName());

        try {
            verifyListingsCollection();
            logger.info("Database verification completed successfully");
        } catch (Exception e) {
            logger.error("Database verification failed: {}", e.getMessage(), e);
            // Don't throw - allow the application to start so connection issues can be
            // troubleshooted without preventing startup
        }
    }

    /**
     * Verifies the listings collection exists, contains data, and has the required indexes.
     */
    private void verifyListingsCollection() {
        MongoCollection<Document> listings = database.getCollection(LISTINGS_COLLECTION);

        // Using estimatedDocumentCount() for better performance (doesn't scan all documents)
        long count = listings.estimatedDocumentCount();

        logger.info("Listings collection found with {} documents", count);

        if (count == 0) {
            logger.warn(
                "Listings collection is empty. Please ensure the sample_airbnb dataset is loaded. " +
                "Visit https://www.mongodb.com/docs/atlas/sample-data/ for instructions."
            );
        }

        createTextSearchIndex(listings);
        createLocationIndex(listings);
        createSupportingIndexes(listings);
        createMongoDBSearchIndex(listings);
        verifyEmbeddings(listings);
    }

    /**
     * Creates a text search index on the listings collection if one does not already exist.
     *
     * <p>The index covers name, summary and description, which enables the {@code $text}
     * operator used by the {@code q} parameter of the list endpoint.
     *
     * <p>MongoDB allows only one text index per collection, so this method checks for <em>any</em>
     * text index rather than one with a specific name.
     */
    private void createTextSearchIndex(MongoCollection<Document> listings) {
        try {
            boolean textIndexExists = false;

            for (Document index : listings.listIndexes()) {
                Document key = index.get("key", Document.class);
                if (key != null && key.containsKey("_fts")) {
                    // _fts is the internal field MongoDB uses for text indexes
                    textIndexExists = true;
                    logger.info("Text search index '{}' already exists on listings collection",
                            index.getString("name"));
                    break;
                }
            }

            if (!textIndexExists) {
                IndexOptions indexOptions = new IndexOptions()
                        .name(TEXT_INDEX_NAME)
                        .background(true);

                listings.createIndex(
                    Indexes.compoundIndex(
                        Indexes.text(Listing.Fields.NAME),
                        Indexes.text(Listing.Fields.SUMMARY),
                        Indexes.text(Listing.Fields.DESCRIPTION)
                    ),
                    indexOptions
                );

                logger.info("Text search index '{}' created successfully for listings collection", TEXT_INDEX_NAME);
            }

        } catch (Exception e) {
            logger.error("Could not create text search index: {}", e.getMessage());
            logger.warn("Text search (the 'q' parameter) may not work without this index");
        }
    }

    /**
     * Creates a 2dsphere index on {@code address.location}.
     *
     * <p>This index is required by the {@code $geoNear} stage behind the /nearby endpoint;
     * without it, proximity queries fail outright rather than merely running slowly.
     */
    private void createLocationIndex(MongoCollection<Document> listings) {
        try {
            if (indexExists(listings, LOCATION_INDEX_NAME)) {
                logger.info("Geospatial index '{}' already exists", LOCATION_INDEX_NAME);
                return;
            }

            IndexOptions indexOptions = new IndexOptions()
                    .name(LOCATION_INDEX_NAME)
                    .background(true);

            listings.createIndex(Indexes.geo2dsphere(Listing.Fields.ADDRESS_LOCATION), indexOptions);

            logger.info("Geospatial index '{}' created successfully on '{}'",
                    LOCATION_INDEX_NAME, Listing.Fields.ADDRESS_LOCATION);

        } catch (Exception e) {
            logger.error("Could not create 2dsphere index: {}", e.getMessage());
            logger.warn("The proximity endpoint (/api/listings/nearby) will not work without this index");
        }
    }

    /**
     * Creates indexes that support the filter, sort and aggregation endpoints.
     *
     * <p>The listings endpoint always sorts, defaulting to {@code name} ascending, and it now
     * counts the matching documents as well as fetching a page of them. Without an index behind
     * the sort key MongoDB has to read the whole collection and order it in memory, and these
     * documents are unusually expensive to hold there because each one embeds its full reviews
     * array. Every field the API can sort by therefore gets an index, as does every field it can
     * filter by.
     *
     * <p>Single-field indexes serve either direction, so ascending is enough for the sort keys
     * even though the API allows descending. The two compound indexes cover the combination the
     * UI produces most often: a dropdown filter applied together with the default sort. In a
     * compound index the equality field has to come first and the sort field second, otherwise
     * the index orders by the wrong key and the sort falls back to memory.
     */
    private void createSupportingIndexes(MongoCollection<Document> listings) {
        createSimpleIndex(listings, PROPERTY_TYPE_INDEX_NAME, Indexes.ascending(Listing.Fields.PROPERTY_TYPE));
        createSimpleIndex(listings, LAST_REVIEW_INDEX_NAME, Indexes.descending(Listing.Fields.LAST_REVIEW));

        // Sort keys. NAME matters most: it is the default, so it applies to every request that
        // does not name another one.
        createSimpleIndex(listings, NAME_INDEX_NAME, Indexes.ascending(Listing.Fields.NAME));
        createSimpleIndex(listings, PRICE_INDEX_NAME, Indexes.ascending(Listing.Fields.PRICE));
        createSimpleIndex(listings, REVIEW_SCORE_INDEX_NAME,
                Indexes.ascending(Listing.Fields.REVIEW_SCORES_RATING));
        createSimpleIndex(listings, NUMBER_OF_REVIEWS_INDEX_NAME,
                Indexes.ascending(Listing.Fields.NUMBER_OF_REVIEWS));
        createSimpleIndex(listings, ACCOMMODATES_INDEX_NAME,
                Indexes.ascending(Listing.Fields.ACCOMMODATES));

        // Filter keys. AMENITIES is an array, so this is a multikey index with one entry per
        // amenity per listing, which is what makes the amenity filter selective.
        createSimpleIndex(listings, ROOM_TYPE_INDEX_NAME, Indexes.ascending(Listing.Fields.ROOM_TYPE));
        createSimpleIndex(listings, MARKET_INDEX_NAME, Indexes.ascending(Listing.Fields.ADDRESS_MARKET));
        createSimpleIndex(listings, COUNTRY_INDEX_NAME, Indexes.ascending(Listing.Fields.ADDRESS_COUNTRY));
        createSimpleIndex(listings, AMENITIES_INDEX_NAME, Indexes.ascending(Listing.Fields.AMENITIES));
        createSimpleIndex(listings, BEDROOMS_INDEX_NAME, Indexes.ascending(Listing.Fields.BEDROOMS));

        // Filter plus default sort, the shape the filter bar produces on nearly every use
        createSimpleIndex(listings, MARKET_NAME_INDEX_NAME,
                Indexes.compoundIndex(
                        Indexes.ascending(Listing.Fields.ADDRESS_MARKET),
                        Indexes.ascending(Listing.Fields.NAME)));
        createSimpleIndex(listings, PROPERTY_TYPE_NAME_INDEX_NAME,
                Indexes.compoundIndex(
                        Indexes.ascending(Listing.Fields.PROPERTY_TYPE),
                        Indexes.ascending(Listing.Fields.NAME)));
    }

    private void createSimpleIndex(MongoCollection<Document> listings, String name, org.bson.conversions.Bson keys) {
        try {
            if (indexExists(listings, name)) {
                logger.info("Index '{}' already exists", name);
                return;
            }

            listings.createIndex(keys, new IndexOptions().name(name).background(true));
            logger.info("Index '{}' created successfully for listings collection", name);

        } catch (Exception e) {
            logger.error("Could not create index '{}': {}", name, e.getMessage());
            logger.warn("Queries relying on index '{}' may be slower", name);
        }
    }

    private boolean indexExists(MongoCollection<Document> collection, String name) {
        for (Document index : collection.listIndexes()) {
            if (name.equals(index.getString("name"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates the MongoDB Search index used by the /search endpoint.
     *
     * <p>The index maps the fields the search endpoint queries:
     * <ul>
     *   <li>name, summary, description, neighborhood_overview - listing prose</li>
     *   <li>amenities - the amenity array, searched as text</li>
     *   <li>host.host_name - nested host name, searched with fuzzy matching</li>
     * </ul>
     *
     * <p>This is different from the text index above: MongoDB Search provides fuzzy matching,
     * phrase search and compound queries. It requires MongoDB Atlas.
     */
    private void createMongoDBSearchIndex(MongoCollection<Document> listings) {
        try {
            boolean indexExists = false;
            for (Document index : listings.listSearchIndexes()) {
                if (MONGODB_SEARCH_INDEX_NAME.equals(index.getString("name"))) {
                    indexExists = true;
                    logger.info("MongoDB Search index '{}' already exists", MONGODB_SEARCH_INDEX_NAME);
                    break;
                }
            }

            if (!indexExists) {
                Document stringField = new Document("type", "string").append("analyzer", "lucene.standard");

                Document indexDefinition = new Document("mappings", new Document()
                        .append("dynamic", false)
                        .append("fields", new Document()
                                .append("name", stringField)
                                .append("summary", stringField)
                                .append("description", stringField)
                                .append("neighborhood_overview", stringField)
                                .append("amenities", stringField)
                                .append("host", new Document()
                                        .append("type", "document")
                                        .append("fields", new Document()
                                                .append("host_name", stringField)))
                        )
                );

                Document createIndexCommand = new Document("createSearchIndexes", LISTINGS_COLLECTION)
                        .append("indexes", Collections.singletonList(
                                new Document("name", MONGODB_SEARCH_INDEX_NAME)
                                        .append("definition", indexDefinition)
                        ));

                database.runCommand(createIndexCommand);

                logger.info("MongoDB Search index '{}' created successfully. The index may take a few moments to build.",
                        MONGODB_SEARCH_INDEX_NAME);
            }

        } catch (Exception e) {
            logger.warn("Could not create MongoDB Search index: {}", e.getMessage());
            logger.warn("If you are using Atlas, the index may already exist or there may be a permissions issue.");
            logger.warn("The search endpoint (/api/listings/search) will not work without this index.");
        }
    }

    /**
     * Checks whether any listing carries a description embedding and, if so, creates the vector
     * search index.
     *
     * <p>Unlike sample_mflix, the {@code sample_airbnb} dataset ships without embeddings. Until
     * the backfill endpoint has run there is nothing to index, so this method logs how to
     * populate them instead of failing.
     */
    private void verifyEmbeddings(MongoCollection<Document> listings) {
        try {
            Document embedded = listings.find(new Document(embeddingField, new Document("$exists", true)))
                    .projection(new Document("_id", 1))
                    .first();

            if (embedded == null) {
                logger.warn(
                    "No listing has a '{}' field yet, so vector search is not available. " +
                    "Set VOYAGE_API_KEY in your .env file and call " +
                    "POST /api/listings/embeddings/backfill to generate embeddings.",
                    embeddingField
                );
                return;
            }

            logger.info("Found listings with '{}' embeddings", embeddingField);
            createVectorSearchIndex(listings);

        } catch (Exception e) {
            logger.error("Could not verify listing embeddings: {}", e.getMessage());
        }
    }

    /**
     * Creates the vector search index on the embedding field if it does not already exist.
     */
    private void createVectorSearchIndex(MongoCollection<Document> listings) {
        try {
            for (Document index : listings.listSearchIndexes()) {
                if (VECTOR_INDEX_NAME.equals(index.getString("name"))) {
                    logger.info("Vector search index '{}' already exists", VECTOR_INDEX_NAME);
                    return;
                }
            }

            Document vectorFieldDefinition = new Document()
                    .append("type", "vector")
                    .append("path", embeddingField)
                    .append("numDimensions", embeddingDimensions)
                    .append("similarity", "cosine");

            Document indexDefinition = new Document()
                    .append("fields", List.of(vectorFieldDefinition));

            Document createIndexCommand = new Document("createSearchIndexes", LISTINGS_COLLECTION)
                    .append("indexes", Collections.singletonList(
                            new Document("name", VECTOR_INDEX_NAME)
                                    .append("type", "vectorSearch")
                                    .append("definition", indexDefinition)
                    ));

            database.runCommand(createIndexCommand);

            logger.info("Vector search index '{}' created successfully. The index may take a few moments to build.",
                    VECTOR_INDEX_NAME);

        } catch (Exception e) {
            logger.error("Failed to create vector search index: {}", e.getMessage());
            logger.warn(
                "To create the vector search index manually, use the Atlas UI to add an index named '{}' with:\n" +
                "  - Field: {}\n" +
                "  - Dimensions: {}\n" +
                "  - Similarity: cosine\n" +
                "See https://www.mongodb.com/docs/atlas/atlas-vector-search/create-index/",
                VECTOR_INDEX_NAME, embeddingField, embeddingDimensions
            );
        }
    }
}
