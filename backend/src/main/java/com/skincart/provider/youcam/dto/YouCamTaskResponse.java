package com.skincart.provider.youcam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YouCamTaskResponse(Integer status, Data data) {

    public record Data(@JsonProperty("task_id") String taskId) {
    }
}
