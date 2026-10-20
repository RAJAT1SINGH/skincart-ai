package com.skincart.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.skincart.common.AnalysisStatus;
import com.skincart.simulation.SkinSimulationFocus;
import org.junit.jupiter.api.Test;

class MockSkinSimulationProviderTest {

    @Test
    void returnsDeterministicResultWithoutNeedingAProviderImageReference() {
        MockSkinSimulationProvider provider = new MockSkinSimulationProvider();

        var submission = provider.submit(null, SkinSimulationFocus.ACNE);
        var first = provider.poll(submission.taskId());
        var second = provider.poll(submission.taskId());

        assertThat(provider.providerName()).isEqualTo("mock");
        assertThat(provider.requiresSourceImageReference()).isFalse();
        assertThat(first).isEqualTo(second);
        assertThat(first.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(first.resultImageUrl()).isEqualTo("/mock-skin-simulation-after.svg");
    }
}
