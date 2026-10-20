package com.skincart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skincart.dto.response.SkinSimulationResponse;
import com.skincart.exception.GlobalExceptionHandler;
import com.skincart.exception.ResourceNotFoundException;
import com.skincart.exception.SkinAnalysisFailedException;
import com.skincart.exception.SkinSimulationNotReadyException;
import com.skincart.exception.SkinSimulationUnavailableException;
import com.skincart.service.SkinSimulationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SkinSimulationController.class)
@Import(GlobalExceptionHandler.class)
class SkinSimulationControllerTest {

    private static final UUID ANALYSIS_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final String PATH = "/api/v1/skin-analysis/" + ANALYSIS_ID + "/simulation";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkinSimulationService service;

    @Test
    void returnsSimulationResultForCompletedAnalysis() throws Exception {
        when(service.simulate(any(), any())).thenReturn(new SkinSimulationResponse(
                ANALYSIS_ID, "pore", "Pore appearance", "https://images.example.test/simulation.jpg", false));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concernCode\":\"pore\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(ANALYSIS_ID.toString()))
                .andExpect(jsonPath("$.concernCode").value("pore"))
                .andExpect(jsonPath("$.resultImageUrl").value("https://images.example.test/simulation.jpg"))
                .andExpect(jsonPath("$.mock").value(false));
    }

    @Test
    void returnsNotFoundForUnknownAnalysis() throws Exception {
        when(service.simulate(any(), any())).thenThrow(new ResourceNotFoundException("Skin analysis"));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concernCode\":\"pore\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsConflictForAnIncompleteAnalysis() throws Exception {
        when(service.simulate(any(), any())).thenThrow(new SkinSimulationNotReadyException());

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concernCode\":\"pore\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_READY"));
    }

    @Test
    void returnsUnprocessableForFailedOrUnsupportedAnalysis() throws Exception {
        doThrow(new SkinAnalysisFailedException()).when(service).simulate(any(), any());
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concernCode\":\"pore\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SKIN_ANALYSIS_FAILED"));

        doThrow(new SkinSimulationUnavailableException(
                        "SIMULATION_NOT_SUPPORTED", "Visual simulation isn't available for this focus yet."))
                .when(service)
                .simulate(any(), any());
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concernCode\":\"redness\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SIMULATION_NOT_SUPPORTED"));
    }

    @Test
    void validatesTheRequestedConcernCode() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concernCode\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
