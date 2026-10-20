package com.skincart.controller;

import com.skincart.dto.request.SkinSimulationRequest;
import com.skincart.dto.response.SkinSimulationResponse;
import com.skincart.service.SkinSimulationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SkinSimulationController {

    private final SkinSimulationService skinSimulationService;

    public SkinSimulationController(SkinSimulationService skinSimulationService) {
        this.skinSimulationService = skinSimulationService;
    }

    @PostMapping("/api/v1/skin-analysis/{analysisId}/simulation")
    public SkinSimulationResponse simulate(
            @PathVariable UUID analysisId, @Valid @RequestBody SkinSimulationRequest request) {
        return skinSimulationService.simulate(analysisId, request);
    }
}
