package com.skincart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skincart.common.AnalysisStatus;
import com.skincart.dto.response.SkinAnalysisCreatedResponse;
import com.skincart.dto.response.SkinAnalysisResponse;
import com.skincart.exception.GlobalExceptionHandler;
import com.skincart.exception.InvalidImageException;
import com.skincart.exception.ResourceNotFoundException;
import com.skincart.provider.ProviderException;
import com.skincart.service.SkinAnalysisService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;

@WebMvcTest(SkinAnalysisController.class)
@Import(GlobalExceptionHandler.class)
class SkinAnalysisControllerTest {

    private static final String BASE_PATH = "/api/v1/skin-analysis";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkinAnalysisService skinAnalysisService;

    @Test
    void createsAnAnalysisAndReturnsLocation() throws Exception {
        UUID analysisId = UUID.randomUUID();
        MockMultipartFile image = new MockMultipartFile(
                "image", "selfie.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        when(skinAnalysisService.create(any(MultipartFile.class)))
                .thenReturn(new SkinAnalysisCreatedResponse(analysisId, AnalysisStatus.CREATED));

        mockMvc.perform(multipart(BASE_PATH).file(image))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE_PATH + "/" + analysisId))
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void rejectsUploadWithoutImage() throws Exception {
        mockMvc.perform(multipart(BASE_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(BASE_PATH));
    }

    @Test
    void mapsInvalidAndOversizedImageErrors() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "bad.png", "image/png", new byte[] {1});
        when(skinAnalysisService.create(any(MultipartFile.class)))
                .thenThrow(new InvalidImageException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "Invalid image."));

        mockMvc.perform(multipart(BASE_PATH).file(image))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));

        when(skinAnalysisService.create(any(MultipartFile.class)))
                .thenThrow(new InvalidImageException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Image exceeds the maximum size."));

        mockMvc.perform(multipart(BASE_PATH).file(image))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void mapsProviderFailureWithoutExposingRawProviderDetailsAndPreservesRetryAfter() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "selfie.png", "image/png", new byte[] {1});
        when(skinAnalysisService.create(any(MultipartFile.class)))
                .thenThrow(new ProviderException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "PROVIDER_RATE_LIMITED",
                        "The skin analysis provider is rate limiting requests.",
                        "30"));

        mockMvc.perform(multipart(BASE_PATH).file(image))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("PROVIDER_RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("The skin analysis provider is rate limiting requests."));
    }

    @Test
    void returnsConsistentErrorWhenAnalysisDoesNotExist() throws Exception {
        UUID analysisId = UUID.randomUUID();
        when(skinAnalysisService.get(analysisId)).thenThrow(new ResourceNotFoundException("Skin analysis"));

        mockMvc.perform(get(BASE_PATH + "/" + analysisId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Skin analysis was not found."))
                .andExpect(jsonPath("$.path").value(BASE_PATH + "/" + analysisId));
    }

    @Test
    void returnsOnlyInternalAnalysisFields() throws Exception {
        UUID analysisId = UUID.randomUUID();
        when(skinAnalysisService.get(analysisId)).thenReturn(new SkinAnalysisResponse(
                analysisId, Instant.parse("2026-01-01T00:00:00Z"), AnalysisStatus.CREATED, null, List.of(), null, null));

        mockMvc.perform(get(BASE_PATH + "/" + analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.providerTaskId").doesNotExist())
                .andExpect(jsonPath("$.providerName").doesNotExist())
                .andExpect(jsonPath("$.originalImageReference").doesNotExist());
    }
}
