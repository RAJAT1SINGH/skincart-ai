package com.skincart.provider;

import com.skincart.common.AnalysisStatus;

public record SkinSimulationSnapshot(AnalysisStatus status, String resultImageUrl) {
}
