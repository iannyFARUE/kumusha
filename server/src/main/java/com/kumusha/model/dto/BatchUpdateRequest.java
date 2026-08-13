package com.kumusha.model.dto;

import java.util.List;

/**
 * Data Transfer Object for updating several listings at once.
 *
 * <p>This DTO is used for PATCH /api/listings requests. Both halves of the request are
 * constrained rather than free-form:
 * <ul>
 *   <li>the targets are an explicit list of ids, so the server builds the {@code _id $in} query
 *       itself and a caller cannot widen the update to documents it did not name;</li>
 *   <li>the changes reuse {@link UpdateListingRequest}, so a batch update can only touch the same
 *       whitelisted fields a single-listing update can, and cannot reach arbitrary stored paths.</li>
 * </ul>
 */
public record BatchUpdateRequest (

    /**
     * Ids of the listings to update. Required, and capped by the service to keep a single request
     * from rewriting an unbounded number of documents.
     */
    List<String> ids,

    /**
     * The fields to change. Any field left null is not written.
     */
    UpdateListingRequest update) {}
