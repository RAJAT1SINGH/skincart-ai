package com.skincart.provider.youcam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YouCamSkinSimulationStatusResponse(Integer status, Data data) {

    public record Data(
            @JsonProperty("task_status") String taskStatus,
            String error,
            @JsonProperty("error_message") String errorMessage,
            Results results) {
    }

    public record Results(String url) {
    }
}
