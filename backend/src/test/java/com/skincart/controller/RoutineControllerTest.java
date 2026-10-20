package com.skincart.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skincart.common.ProductCategory;
import com.skincart.dto.response.RoutineResponse;
import com.skincart.dto.response.RoutineStepResponse;
import com.skincart.exception.GlobalExceptionHandler;
import com.skincart.exception.RecommendationNotReadyException;
import com.skincart.exception.ResourceNotFoundException;
import com.skincart.exception.SkinAnalysisFailedException;
import com.skincart.routine.RoutineStep;
import com.skincart.service.RoutineService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoutineController.class)
@Import(GlobalExceptionHandler.class)
class RoutineControllerTest {

    private static final UUID ANALYSIS_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final String PATH = "/api/v1/skin-analysis/" + ANALYSIS_ID + "/routine";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoutineService routineService;

    @Test
    void returnsMorningAndEveningRoutineForCompletedAnalysis() throws Exception {
        when(routineService.getRoutine(ANALYSIS_ID)).thenReturn(new RoutineResponse(
                ANALYSIS_ID,
                List.of(new RoutineStepResponse(
                        RoutineStep.CLEANSER,
                        UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                        "Demo Cleanser",
                        ProductCategory.CLEANSER,
                        "Selected because it matches the acne concern identified in your skin analysis.")),
                List.of()));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(ANALYSIS_ID.toString()))
                .andExpect(jsonPath("$.morning[0].step").value("CLEANSER"))
                .andExpect(jsonPath("$.morning[0].category").value("CLEANSER"))
                .andExpect(jsonPath("$.morning[0].reason").exists())
                .andExpect(jsonPath("$.evening").isArray());
    }

    @Test
    void returnsNotFoundForUnknownAnalysis() throws Exception {
        when(routineService.getRoutine(ANALYSIS_ID)).thenThrow(new ResourceNotFoundException("Skin analysis"));

        mockMvc.perform(get(PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsConflictForIncompleteAnalysis() throws Exception {
        when(routineService.getRoutine(ANALYSIS_ID)).thenThrow(new RecommendationNotReadyException());

        mockMvc.perform(get(PATH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOMMENDATIONS_NOT_AVAILABLE"));
    }

    @Test
    void returnsUnprocessableEntityForFailedAnalysis() throws Exception {
        when(routineService.getRoutine(ANALYSIS_ID)).thenThrow(new SkinAnalysisFailedException());

        mockMvc.perform(get(PATH))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SKIN_ANALYSIS_FAILED"));
    }
}
