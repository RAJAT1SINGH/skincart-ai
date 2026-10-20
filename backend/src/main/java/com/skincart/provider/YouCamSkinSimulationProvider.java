package com.skincart.provider;

import com.skincart.config.YouCamProperties;
import com.skincart.provider.youcam.YouCamSkinSimulationClient;
import com.skincart.simulation.SkinSimulationFocus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "youcam", name = "mock", havingValue = "false")
public class YouCamSkinSimulationProvider implements SkinSimulationProvider {

    private final YouCamSkinSimulationClient skinSimulationClient;

    public YouCamSkinSimulationProvider(YouCamProperties properties, YouCamSkinSimulationClient skinSimulationClient) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("YOUCAM_API_KEY must be configured when YOUCAM_MOCK=false.");
        }
        this.skinSimulationClient = skinSimulationClient;
    }

    @Override
    public String providerName() {
        return "youcam";
    }

    @Override
    public boolean requiresSourceImageReference() {
        return true;
    }

    @Override
    public SkinSimulationSubmission submit(String sourceImageReference, SkinSimulationFocus focus) {
        return skinSimulationClient.createTask(sourceImageReference, focus);
    }

    @Override
    public SkinSimulationSnapshot poll(String taskId) {
        return skinSimulationClient.getTask(taskId);
    }
}
