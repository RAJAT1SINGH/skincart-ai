package com.skincart.provider.youcam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record YouCamSkinAnalysisRequest(
        @JsonProperty("src_file_id") String sourceFileId,
        @JsonProperty("dst_actions") List<String> destinationActions,
        String format) {
}
