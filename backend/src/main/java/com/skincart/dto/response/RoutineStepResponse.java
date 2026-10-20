package com.skincart.dto.response;

import com.skincart.common.ProductCategory;
import com.skincart.routine.RoutineStep;
import java.util.UUID;

public record RoutineStepResponse(
        RoutineStep step,
        UUID productId,
        String productName,
        ProductCategory category,
        String reason) {}
