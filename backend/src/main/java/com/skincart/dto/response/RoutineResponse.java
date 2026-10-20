package com.skincart.dto.response;

import java.util.List;
import java.util.UUID;

public record RoutineResponse(UUID analysisId, List<RoutineStepResponse> morning, List<RoutineStepResponse> evening) {

    public RoutineResponse {
        morning = List.copyOf(morning);
        evening = List.copyOf(evening);
    }
}
