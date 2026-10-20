package com.skincart.provider.youcam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record YouCamFileMetadataRequest(List<FileMetadata> files) {

    public record FileMetadata(
            @JsonProperty("content_type") String contentType,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("file_size") long fileSize) {
    }
}
