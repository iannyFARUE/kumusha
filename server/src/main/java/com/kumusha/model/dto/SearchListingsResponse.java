package com.kumusha.model.dto;

import com.kumusha.model.Listing;
import java.util.List;
import lombok.Builder;

/**
 * Response wrapper for listing search results.
 *
 * <p>This DTO wraps the search results with a total count so the client can paginate.
 */
@Builder
public record SearchListingsResponse (

    /**
     * Listings matching the search criteria.
     */
    List<Listing> listings,

    /**
     * Total count of listings returned for the current search page.
     */
    Integer totalCount) {}
