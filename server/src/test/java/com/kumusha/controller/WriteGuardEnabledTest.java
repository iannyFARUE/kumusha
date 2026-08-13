package com.kumusha.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kumusha.model.dto.DeleteResponse;
import com.kumusha.model.dto.EmbeddingBackfillResponse;
import com.kumusha.service.ListingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Confirms that the read-only guard actually opens when writes are enabled.
 *
 * <p>Without this, a guard that rejected every write unconditionally would still pass
 * {@link WriteGuardTest}, and the configuration flag would be doing nothing.
 */
@WebMvcTest(ListingControllerImpl.class)
@TestPropertySource(properties = "kumusha.write.enabled=true")
@DisplayName("Write Guard - writes enabled")
class WriteGuardEnabledTest {

    private static final String LISTING_ID = "10006546";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListingService listingService;

    @Test
    @DisplayName("DELETE /api/listings/{id} reaches the service")
    void singleDeleteIsAllowed() throws Exception {
        when(listingService.deleteListing(anyString())).thenReturn(new DeleteResponse(1L));

        mockMvc.perform(delete("/api/listings/{id}", LISTING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(listingService).deleteListing(LISTING_ID);
    }

    @Test
    @DisplayName("The embedding backfill reaches the service")
    void backfillIsAllowed() throws Exception {
        when(listingService.backfillDescriptionEmbeddings(any()))
                .thenReturn(EmbeddingBackfillResponse.builder()
                        .embeddedCount(0)
                        .skippedCount(0)
                        .remainingCount(0L)
                        .embeddingField("description_embedding")
                        .dimensions(2048)
                        .build());

        mockMvc.perform(post("/api/listings/embeddings/backfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
