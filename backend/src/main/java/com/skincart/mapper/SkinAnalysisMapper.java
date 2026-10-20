package com.skincart.mapper;

import com.skincart.entity.SkinAnalysis;
import com.skincart.entity.SkinConcern;
import com.skincart.dto.response.SkinAnalysisCreatedResponse;
import com.skincart.dto.response.SkinAnalysisResponse;
import com.skincart.dto.response.SkinConcernResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SkinAnalysisMapper {

    public SkinAnalysisCreatedResponse toCreatedResponse(SkinAnalysis analysis) {
        return new SkinAnalysisCreatedResponse(analysis.getId(), analysis.getStatus());
    }

    public SkinAnalysisResponse toResponse(SkinAnalysis analysis, List<SkinConcern> concerns) {
        List<SkinConcernResponse> concernResponses = concerns.stream()
                .map(concern -> new SkinConcernResponse(
                        concern.getConcernCode(),
                        concern.getDisplayName(),
                        concern.getSeverity(),
                        concern.getUiScore(),
                        concern.getRawScore(),
                        concern.getMaskUrls()))
                .toList();

        return new SkinAnalysisResponse(
                analysis.getId(),
                analysis.getCreatedAt(),
                analysis.getStatus(),
                analysis.getOverallScore(),
                concernResponses,
                analysis.getErrorCode(),
                analysis.getErrorMessage());
    }
}
