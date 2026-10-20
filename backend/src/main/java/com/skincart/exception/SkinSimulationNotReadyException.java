package com.skincart.exception;

import org.springframework.http.HttpStatus;

public class SkinSimulationNotReadyException extends ApplicationException {

    public SkinSimulationNotReadyException() {
        super(HttpStatus.CONFLICT, "ANALYSIS_NOT_READY", "Visual simulation is available after analysis completes.");
    }
}
