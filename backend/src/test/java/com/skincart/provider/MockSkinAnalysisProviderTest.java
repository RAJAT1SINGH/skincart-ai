package com.skincart.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.skincart.common.AnalysisStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockSkinAnalysisProviderTest {

    @Test
    void returnsDeterministicMockResultsForFourMvpConcerns() {
        MockSkinAnalysisProvider provider = new MockSkinAnalysisProvider();
        SkinAnalysisImage image = new SkinAnalysisImage(new byte[] {1}, "selfie.png", "image/png");

        SkinAnalysisSubmission firstSubmission = provider.submit(image);
        SkinAnalysisSnapshot firstResult = provider.poll(firstSubmission.taskId());
        SkinAnalysisSnapshot secondResult = provider.poll(firstSubmission.taskId());

        assertThat(provider.providerName()).isEqualTo("mock");
        assertThat(firstSubmission.taskId()).isEqualTo("mock-skin-analysis-task");
        assertThat(firstResult.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(firstResult).isEqualTo(secondResult);
        assertThat(firstResult.result().concerns())
                .extracting(SkinConcernResult::name)
                .containsExactly("wrinkle", "pore", "texture", "acne");
        assertThat(firstResult.result().concerns())
                .allSatisfy(concern -> assertThat(concern.maskUrls()).isEqualTo(List.of()));
    }
}
