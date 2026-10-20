package com.skincart.provider;

public record SkinAnalysisSubmission(String taskId, String sourceImageReference) {

    public SkinAnalysisSubmission(String taskId) {
        this(taskId, null);
    }
}
