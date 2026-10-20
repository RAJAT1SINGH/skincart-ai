package com.skincart.provider;

import com.skincart.common.AnalysisStatus;
import com.skincart.simulation.SkinSimulationFocus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "youcam", name = "mock", havingValue = "true", matchIfMissing = true)
public class MockSkinSimulationProvider implements SkinSimulationProvider {

    private static final String MOCK_TASK_ID = "mock-skin-simulation-task";
    private static final SkinSimulationSnapshot MOCK_RESULT =
            new SkinSimulationSnapshot(AnalysisStatus.COMPLETED, "/mock-skin-simulation-after.svg");

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public boolean requiresSourceImageReference() {
        return false;
    }

    @Override
    public SkinSimulationSubmission submit(String sourceImageReference, SkinSimulationFocus focus) {
        return new SkinSimulationSubmission(MOCK_TASK_ID);
    }

    @Override
    public SkinSimulationSnapshot poll(String taskId) {
        return MOCK_RESULT;
    }
}
