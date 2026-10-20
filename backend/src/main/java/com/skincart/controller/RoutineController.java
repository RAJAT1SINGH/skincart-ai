package com.skincart.controller;

import com.skincart.dto.response.RoutineResponse;
import com.skincart.service.RoutineService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoutineController {

    private final RoutineService routineService;

    public RoutineController(RoutineService routineService) {
        this.routineService = routineService;
    }

    @GetMapping("/api/v1/skin-analysis/{analysisId}/routine")
    public RoutineResponse routine(@PathVariable UUID analysisId) {
        return routineService.getRoutine(analysisId);
    }
}
