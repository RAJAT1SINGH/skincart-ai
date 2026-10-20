package com.skincart.provider.youcam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record YouCamFileMetadataResponse(Integer status, Data data) {

    public record Data(List<FileData> files) {
    }

    public record FileData(
            @JsonProperty("content_type") String contentType,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("file_id") String fileId,
            List<UploadRequest> requests) {
    }

    public record UploadRequest(String method, String url, Map<String, String> headers) {
    }
}
