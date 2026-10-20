package com.skincart.provider.youcam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YouCamErrorResponse(
        Integer status,
        String error,
        @JsonProperty("error_code") String errorCode) {
}
