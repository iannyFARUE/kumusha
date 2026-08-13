package com.kumusha.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kumusha.model.Listing;
import com.kumusha.model.dto.ListingSearchQuery;
import com.kumusha.service.ListingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the read-only guard in its default state, where writes are disabled.
 *
 * <p>This guard is what keeps a public deployment safe given that the write endpoints carry no
 * authentication, so the assertions check not just the status code but that the request never
 * reaches the service: a guard that rejected the response after performing the write would be
 * worthless. Reads are asserted to keep working, since a read-only deployment is still expected
 * to serve the browsing experience.
 *
 * <p>The opposite state is covered by {@link WriteGuardEnabledTest} and, more broadly, by the
 * write cases throughout {@link ListingControllerTest}.
 */
@WebMvcTest(ListingControllerImpl.class)
@TestPropertySource(properties = "kumusha.write.enabled=false")
@DisplayName("Write Guard - writes disabled (default)")
class WriteGuardTest {

    private static final String LISTING_ID = "10006546";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListingService listingService;

    @Test
    @DisplayName("POST /api/listings is rejected with 403 and a WRITE_OPERATIONS_DISABLED code")
    void createIsRejected() throws Exception {
        mockMvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Listing\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("WRITE_OPERATIONS_DISABLED"));

        verify(listingService, never()).createListing(any());
    }

    @Test
    @DisplayName("Batch DELETE /api/listings is rejected and never reaches the service")
    void batchDeleteIsRejected() throws Exception {
        mockMvc.perform(delete("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + LISTING_ID + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WRITE_OPERATIONS_DISABLED"));

        verify(listingService, never()).deleteListingsBatch(any());
    }

    @Test
    @DisplayName("Batch PATCH /api/listings is rejected and never reaches the service")
    void batchUpdateIsRejected() throws Exception {
        mockMvc.perform(patch("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + LISTING_ID + "\"],\"update\":{\"name\":\"x\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WRITE_OPERATIONS_DISABLED"));

        verify(listingService, never()).updateListingsBatch(any(), any());
    }

    @Test
    @DisplayName("DELETE /api/listings/{id} is rejected and never reaches the service")
    void singleDeleteIsRejected() throws Exception {
        mockMvc.perform(delete("/api/listings/{id}", LISTING_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WRITE_OPERATIONS_DISABLED"));

        verify(listingService, never()).deleteListing(anyString());
    }

    @Test
    @DisplayName("The embedding backfill is rejected, so a read-only deployment cannot be billed")
    void backfillIsRejected() throws Exception {
        mockMvc.perform(post("/api/listings/embeddings/backfill"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WRITE_OPERATIONS_DISABLED"));

        verify(listingService, never()).backfillDescriptionEmbeddings(any());
    }

    @Test
    @DisplayName("Reads still work, so a read-only deployment still serves the browsing UI")
    void readsStillWork() throws Exception {
        when(listingService.getAllListings(any(ListingSearchQuery.class)))
                .thenReturn(List.of(Listing.builder().id(LISTING_ID).name("Sea view").build()));

        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
