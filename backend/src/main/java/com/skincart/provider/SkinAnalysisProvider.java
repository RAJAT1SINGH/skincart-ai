package com.skincart.provider;

public interface SkinAnalysisProvider {

    String providerName();

    SkinAnalysisSubmission submit(SkinAnalysisImage image);

    SkinAnalysisSnapshot poll(String taskId);
}
