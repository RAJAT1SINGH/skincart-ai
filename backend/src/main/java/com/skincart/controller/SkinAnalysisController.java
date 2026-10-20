package com.skincart.controller;

import com.skincart.dto.request.SkinAnalysisUploadRequest;
import com.skincart.dto.response.SkinAnalysisCreatedResponse;
import com.skincart.dto.response.SkinAnalysisResponse;
import com.skincart.service.SkinAnalysisService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skin-analysis")
public class SkinAnalysisController {

    private final SkinAnalysisService skinAnalysisService;

    public SkinAnalysisController(SkinAnalysisService skinAnalysisService) {
        this.skinAnalysisService = skinAnalysisService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SkinAnalysisCreatedResponse> create(
            @Valid @ModelAttribute SkinAnalysisUploadRequest request) {
        SkinAnalysisCreatedResponse response = skinAnalysisService.create(request.getImage());
        URI location = URI.create("/api/v1/skin-analysis/" + response.analysisId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{analysisId}")
    public SkinAnalysisResponse get(@PathVariable UUID analysisId) {
        return skinAnalysisService.get(analysisId);
    }
}
