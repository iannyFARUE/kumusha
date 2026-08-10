package com.kumusha.model.dto;

import lombok.Builder;

/**
 * Data Transfer Object for MongoDB Search query parameters.
 *
 * <p>This DTO is used to parse and validate query parameters for GET /api/listings/search
 * requests. It supports searching across multiple fields using MongoDB Search with compound
 * operators.
 */
@Builder
public record ListingSearchRequest (

    /**
     * Search query for the summary field.
     * Uses the phrase operator for exact phrase matching.
     */
    String summary,

    /**
     * Search query for the description field.
     * Uses the phrase operator for exact phrase matching.
     */
    String description,

    /**
     * Search query for the neighborhood_overview field.
     * Uses the phrase operator for exact phrase matching.
     */
    String neighborhood,

    /**
     * Search query for the listing name.
     * Uses the text operator with fuzzy matching for typo tolerance.
     */
    String name,

    /**
     * Search query for the host name.
     * Uses the text operator with fuzzy matching for typo tolerance.
     */
    String host,

    /**
     * Search query for the amenities array.
     * Uses the text operator with fuzzy matching for typo tolerance.
     */
    String amenities,

    /**
     * Maximum number of results to return.
     * Default: 20, Range: 1-100
     */
    Integer limit,

    /**
     * Number of results to skip for pagination.
     * Default: 0, Minimum: 0
     */
    Integer skip,

    /**
     * Compound search operator to use.
     * Valid values: "must", "should", "mustNot", "filter"
     * Default: "must"
     *
     * <ul>
     * <li><b>must</b> - All clauses must match (AND logic)</li>
     * <li><b>should</b> - At least one clause should match (OR logic)</li>
     * <li><b>mustNot</b> - Clauses must not match (NOT logic)</li>
     * <li><b>filter</b> - Clauses must match but don't affect scoring</li>
     * </ul>
     */
    String searchOperator) {

    /**
     * Checks if at least one search field is provided.
     *
     * @return true if at least one search field has a value
     */
    public boolean hasSearchFields() {
        return isPresent(summary)
                || isPresent(description)
                || isPresent(neighborhood)
                || isPresent(name)
                || isPresent(host)
                || isPresent(amenities);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
