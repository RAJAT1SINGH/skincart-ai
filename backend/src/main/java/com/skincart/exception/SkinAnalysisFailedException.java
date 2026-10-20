package com.skincart.exception;

import org.springframework.http.HttpStatus;

public class SkinAnalysisFailedException extends ApplicationException {

    public SkinAnalysisFailedException() {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "SKIN_ANALYSIS_FAILED",
                "Recommendations cannot be generated because the skin analysis failed.");
    }
}
