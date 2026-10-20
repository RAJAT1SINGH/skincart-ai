package com.skincart.service;

import com.skincart.dto.response.RoutineResponse;
import com.skincart.routine.RoutinePlanner;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RoutineService {

    private final RecommendationService recommendationService;
    private final RoutinePlanner routinePlanner;

    public RoutineService(RecommendationService recommendationService, RoutinePlanner routinePlanner) {
        this.recommendationService = recommendationService;
        this.routinePlanner = routinePlanner;
    }

    public RoutineResponse getRoutine(UUID analysisId) {
        return routinePlanner.plan(analysisId, recommendationService.getRankedRecommendations(analysisId));
    }
}
