package com.kumusha.model.dto;

import com.kumusha.model.Listing;
import java.util.List;
import lombok.Builder;

/**
 * Response wrapper for a page of listings.
 *
 * <p>The endpoint previously returned a bare array, which left the client unable to say how many
 * listings matched or how many pages there were: it could only guess whether another page existed
 * by asking for one more row than it intended to show. Returning the total alongside the page
 * makes both answerable.
 */
@Builder
public record ListingsPageResponse (

    /**
     * The listings on this page.
     */
    List<Listing> listings,

    /**
     * How many listings match the filters in total, ignoring paging.
     */
    long totalCount,

    /**
     * The page size actually applied, after clamping.
     */
    int limit,

    /**
     * The offset actually applied, after clamping.
     */
    int skip) {}
