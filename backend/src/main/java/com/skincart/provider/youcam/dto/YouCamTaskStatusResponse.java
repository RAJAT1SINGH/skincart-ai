package com.skincart.provider.youcam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record YouCamTaskStatusResponse(Integer status, Data data) {

    public record Data(
            @JsonProperty("task_status") String taskStatus,
            String error,
            @JsonProperty("error_message") String errorMessage,
            Results results) {
    }

    public record Results(List<Output> output) {
    }

    public record Output(
            String type,
            @JsonProperty("ui_score") Integer uiScore,
            @JsonProperty("raw_score") BigDecimal rawScore,
            @JsonProperty("mask_urls") List<String> maskUrls) {
    }
}
