package com.skincart.provider.youcam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skincart.simulation.SkinSimulationFocus;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record YouCamSkinSimulationRequest(
        @JsonProperty("src_file_id") String sourceFileId,
        BigDecimal acne,
        BigDecimal wrinkle,
        BigDecimal pores,
        BigDecimal texture) {

    private static final BigDecimal MVP_INTENSITY = new BigDecimal("0.3");

    public static YouCamSkinSimulationRequest forFocus(String sourceFileId, SkinSimulationFocus focus) {
        return switch (focus) {
            case ACNE -> new YouCamSkinSimulationRequest(sourceFileId, MVP_INTENSITY, null, null, null);
            case WRINKLE -> new YouCamSkinSimulationRequest(sourceFileId, null, MVP_INTENSITY, null, null);
            case PORE -> new YouCamSkinSimulationRequest(sourceFileId, null, null, MVP_INTENSITY, null);
            case TEXTURE -> new YouCamSkinSimulationRequest(sourceFileId, null, null, null, MVP_INTENSITY);
        };
    }
}
