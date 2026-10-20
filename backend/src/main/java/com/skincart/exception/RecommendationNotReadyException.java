package com.skincart.exception;

import org.springframework.http.HttpStatus;

public class RecommendationNotReadyException extends ApplicationException {

    public RecommendationNotReadyException() {
        super(
                HttpStatus.CONFLICT,
                "RECOMMENDATIONS_NOT_AVAILABLE",
                "Recommendations are not available until the skin analysis is complete.");
    }
}
