package com.skincart.provider;

import com.skincart.config.YouCamProperties;
import com.skincart.provider.youcam.YouCamFileClient;
import com.skincart.provider.youcam.YouCamSkinAnalysisClient;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "youcam", name = "mock", havingValue = "false")
public class YouCamSkinAnalysisProvider implements SkinAnalysisProvider {

    private final List<String> concerns;
    private final YouCamFileClient fileClient;
    private final YouCamSkinAnalysisClient skinAnalysisClient;

    public YouCamSkinAnalysisProvider(
            YouCamProperties properties,
            YouCamFileClient fileClient,
            YouCamSkinAnalysisClient skinAnalysisClient) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("YOUCAM_API_KEY must be configured when YOUCAM_MOCK=false.");
        }
        List<String> configuredConcerns = properties.getConcerns();
        if (configuredConcerns == null || configuredConcerns.isEmpty()) {
            throw new IllegalStateException("At least one YouCam skin analysis concern must be configured.");
        }
        if (configuredConcerns.stream().anyMatch(action -> action == null || action.isBlank())) {
            throw new IllegalStateException("YouCam skin analysis concerns must not be blank.");
        }
        boolean hasHdAction = configuredConcerns.stream().anyMatch(action -> action.startsWith("hd_"));
        boolean hasSdAction = configuredConcerns.stream().anyMatch(action -> !action.startsWith("hd_"));
        if (hasHdAction && hasSdAction) {
            throw new IllegalStateException("HD and SD YouCam skin analysis concerns cannot be mixed.");
        }
        this.concerns = List.copyOf(configuredConcerns);
        this.fileClient = fileClient;
        this.skinAnalysisClient = skinAnalysisClient;
    }

    @Override
    public String providerName() {
        return "youcam";
    }

    @Override
    public SkinAnalysisSubmission submit(SkinAnalysisImage image) {
        String fileId = fileClient.uploadImage(image);
        return skinAnalysisClient.createTask(fileId, concerns);
    }

    @Override
    public SkinAnalysisSnapshot poll(String taskId) {
        return skinAnalysisClient.getTask(taskId);
    }
}
