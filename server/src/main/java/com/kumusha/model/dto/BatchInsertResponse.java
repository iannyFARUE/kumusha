package com.kumusha.model.dto;

import java.util.Collection;

/**
 * Response DTO for batch insert operations.
 *
 * <p>Ids are strings because {@code sample_airbnb} documents use string {@code _id} values.
 */
public record BatchInsertResponse (
        int insertedCount,
        Collection<String> insertedIds) {}
