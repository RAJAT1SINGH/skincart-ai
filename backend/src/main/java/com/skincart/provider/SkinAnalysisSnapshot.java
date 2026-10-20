package com.skincart.provider;

import com.skincart.common.AnalysisStatus;

public record SkinAnalysisSnapshot(AnalysisStatus status, SkinAnalysisResult result) {
}
