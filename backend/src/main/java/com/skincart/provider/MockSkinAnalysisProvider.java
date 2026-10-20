package com.skincart.provider;

import com.skincart.common.AnalysisStatus;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "youcam", name = "mock", havingValue = "true", matchIfMissing = true)
public class MockSkinAnalysisProvider implements SkinAnalysisProvider {

    private static final String MOCK_TASK_ID = "mock-skin-analysis-task";
    private static final SkinAnalysisSnapshot MOCK_RESULT = new SkinAnalysisSnapshot(
            AnalysisStatus.COMPLETED,
            new SkinAnalysisResult(
                    null,
                    List.of(
                            new SkinConcernResult("wrinkle", 78, new BigDecimal("71.25"), List.of()),
                            new SkinConcernResult("pore", 66, new BigDecimal("59.80"), List.of()),
                            new SkinConcernResult("texture", 82, new BigDecimal("76.10"), List.of()),
                            new SkinConcernResult("acne", 73, new BigDecimal("68.45"), List.of()))));

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public SkinAnalysisSubmission submit(SkinAnalysisImage image) {
        return new SkinAnalysisSubmission(MOCK_TASK_ID);
    }

    @Override
    public SkinAnalysisSnapshot poll(String taskId) {
        return MOCK_RESULT;
    }
}
