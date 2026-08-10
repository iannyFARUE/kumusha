package com.kumusha.model.dto;

import lombok.Builder;

/**
 * Response DTO for the description embedding backfill operation.
 *
 * <p>The stock {@code sample_airbnb} dataset contains no embeddings, so vector search only
 * works after this backfill has populated the
 * {@code description_embedding} field on at least some listings.
 */
@Builder
public record EmbeddingBackfillResponse (

    /**
     * Number of listings that were sent to Voyage AI and updated in this run.
     */
    int embeddedCount,

    /**
     * Number of listings skipped because they had no usable description text.
     */
    int skippedCount,

    /**
     * Number of listings that still have no embedding after this run.
     */
    long remainingCount,

    /**
     * Name of the field the embeddings were written to.
     */
    String embeddingField,

    /**
     * Dimension count of the generated vectors. Must match the vector search index.
     */
    int dimensions) {}
