package com.kumusha.model.dto;

import java.util.List;

/**
 * Data Transfer Object for deleting several listings at once.
 *
 * <p>This DTO is used for DELETE /api/listings requests. It carries an explicit list of listing
 * ids rather than a MongoDB filter document: the server builds the {@code _id $in} query itself,
 * so a caller cannot widen the deletion to documents it did not name.
 */
public record BatchDeleteRequest (

    /**
     * Ids of the listings to delete. Required, and capped by the service to keep a single request
     * from removing an unbounded number of documents.
     */
    List<String> ids) {}
