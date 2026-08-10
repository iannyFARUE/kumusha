package com.kumusha.repository;

import com.kumusha.model.Listing;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for listing data access.
 *
 * <p>This repository extends MongoRepository which provides:
 * - Basic CRUD operations (save, findById, findAll, delete, etc.)
 * - Pagination and sorting support
 * - Query derivation from method names
 * - Custom query support via @Query annotation
 *
 * <p>The id type is {@link String} rather than ObjectId because documents in
 * {@code sample_airbnb.listingsAndReviews} use string {@code _id} values.
 *
 * <p>For complex queries not supported by Spring Data, inject MongoTemplate in the service layer.
 */
@Repository
public interface ListingRepository extends MongoRepository<Listing, String> {

    // Spring Data MongoDB provides these methods automatically:
    // - save(Listing listing) - insert or update
    // - saveAll(Iterable<Listing> listings) - batch insert/update
    // - findById(String id) - find by ID
    // - findAll() - find all documents
    // - findAll(Pageable pageable) - find with pagination
    // - deleteById(String id) - delete by ID
    // - delete(Listing listing) - delete entity
    // - count() - count all documents
    // - existsById(String id) - check if exists

    // Custom query methods can be added here using method name conventions:
    // Example: List<Listing> findByAmenitiesContaining(String amenity);
    // Example: List<Listing> findByAccommodatesGreaterThanEqual(Integer guests);
}
