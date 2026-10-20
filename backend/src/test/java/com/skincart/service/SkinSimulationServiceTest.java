package com.skincart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skincart.common.AnalysisStatus;
import com.skincart.dto.request.SkinSimulationRequest;
import com.skincart.entity.SkinAnalysis;
import com.skincart.entity.SkinConcern;
import com.skincart.exception.ResourceNotFoundException;
import com.skincart.exception.SkinAnalysisFailedException;
import com.skincart.exception.SkinSimulationNotReadyException;
import com.skincart.exception.SkinSimulationUnavailableException;
import com.skincart.provider.ProviderException;
import com.skincart.provider.ProviderTimeoutException;
import com.skincart.provider.SkinSimulationProvider;
import com.skincart.provider.SkinSimulationSnapshot;
import com.skincart.provider.SkinSimulationSubmission;
import com.skincart.repository.SkinAnalysisRepository;
import com.skincart.repository.SkinConcernRepository;
import com.skincart.simulation.SkinSimulationFocus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SkinSimulationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-10T00:00:00Z");
    private static final UUID ANALYSIS_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Mock
    private SkinAnalysisRepository skinAnalysisRepository;

    @Mock
    private SkinConcernRepository skinConcernRepository;

    @Mock
    private SkinSimulationProvider provider;

    private SkinSimulationService service;
    private SkinAnalysis completedAnalysis;

    @BeforeEach
    void setUp() {
        service = new SkinSimulationService(
                skinAnalysisRepository,
                skinConcernRepository,
                provider,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ZERO,
                6);
        completedAnalysis = new SkinAnalysis(NOW.minus(Duration.ofDays(1)), AnalysisStatus.COMPLETED);
        completedAnalysis.setOriginalImageReference("opaque-file-reference");
        lenient().when(provider.providerName()).thenReturn("youcam");
        lenient().when(provider.requiresSourceImageReference()).thenReturn(true);
        lenient().when(skinAnalysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(completedAnalysis));
        lenient().when(skinConcernRepository.findAllByAnalysisIdOrderByConcernCodeAsc(ANALYSIS_ID))
                .thenReturn(List.of(concern("pore"), concern("acne")));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void mapsOnlyAnAnalyzedSupportedFocusAndReturnsTheCompletedImage(CapturedOutput output) {
        when(provider.submit("opaque-file-reference", SkinSimulationFocus.PORE))
                .thenReturn(new SkinSimulationSubmission("simulation-task"));
        when(provider.poll("simulation-task"))
                .thenReturn(new SkinSimulationSnapshot(AnalysisStatus.PROCESSING, null))
                .thenReturn(new SkinSimulationSnapshot(
                        AnalysisStatus.COMPLETED, "https://images.example.test/temporary-result.jpg"));

        var response = service.simulate(ANALYSIS_ID, new SkinSimulationRequest("pore"));

        assertThat(response.analysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(response.concernCode()).isEqualTo("pore");
        assertThat(response.focusLabel()).isEqualTo("Pore appearance");
        assertThat(response.resultImageUrl()).isEqualTo("https://images.example.test/temporary-result.jpg");
        assertThat(response.mock()).isFalse();
        String logs = output.getOut() + output.getErr();
        assertThat(logs)
                .contains("Skin simulation request started")
                .contains("task submission started: provider=youcam, focus=pore")
                .contains("polling attempt 1/6: provider=youcam")
                .contains("taskStatus=PROCESSING")
                .contains("Skin simulation completed: provider=youcam")
                .doesNotContain("opaque-file-reference", "simulation-task");
        verify(provider).submit("opaque-file-reference", SkinSimulationFocus.PORE);
        verify(provider, org.mockito.Mockito.times(2)).poll("simulation-task");
    }

    @Test
    void rejectsAnUnknownAnalysis() {
        when(skinAnalysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.simulate(ANALYSIS_ID, new SkinSimulationRequest("pore")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(provider, never()).submit(any(), any());
    }

    @Test
    void rejectsAnAnalysisThatIsStillProcessing() {
        completedAnalysis = new SkinAnalysis(NOW, AnalysisStatus.PROCESSING);
        when(skinAnalysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(completedAnalysis));

        assertThatThrownBy(() -> service.simulate(ANALYSIS_ID, new SkinSimulationRequest("pore")))
                .isInstanceOf(SkinSimulationNotReadyException.class);
        verify(provider, never()).submit(any(), any());
    }

    @Test
    void rejectsAnAnalysisThatFailed() {
        completedAnalysis = new SkinAnalysis(NOW, AnalysisStatus.FAILED);
        when(skinAnalysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(completedAnalysis));

        assertThatThrownBy(() -> service.simulate(ANALYSIS_ID, new SkinSimulationRequest("pore")))
                .isInstanceOf(SkinAnalysisFailedException.class);
        verify(provider, never()).submit(any(), any());
    }

    @Test
    void rejectsAConcernThatWasNotReturnedByTheAnalysis() {
        assertThatThrownBy(() -> service.simulate(ANALYSIS_ID, new SkinSimulationRequest("wrinkle")))
                .isInstanceOfSatisfying(SkinSimulationUnavailableException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("SIMULATION_FOCUS_NOT_ANALYZED"));
        verify(provider, never()).submit(any(), any());
    }

    @Test
    void rejectsAnAnalyzedConcernWithoutAMvpMapping() {
        when(skinConcernRepository.findAllByAnalysisIdOrderByConcernCodeAsc(ANALYSIS_ID))
                .thenReturn(List.of(concern("redness")));

        assertThatThrownBy(() -> service.simulate(ANALYSIS_ID, new SkinSimulationRequest("redness")))
                .isInstanceOfSatisfying(SkinSimulationUnavailableException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("SIMULATION_NOT_SUPPORTED"));
        verify(provider, never()).submit(any(), any());
    }

    @Test
    void refusesToUseTheSourceFileReferenceAfterTheDocumentedThirtyDayRetention() {
        SkinAnalysis expired = new SkinAnalysis(NOW.minus(Duration.ofDays(30)), AnalysisStatus.COMPLETED);
        expired.setOriginalImageReference("opaque-file-reference");
        when(skinAnalysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.simulate(ANALYSIS_ID, new SkinSimulationRequest("pore")))
                .isInstanceOfSatisfying(SkinSimulationUnavailableException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("SIMULATION_SOURCE_EXPIRED"));
        verify(provider, never()).submit(any(), any());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void propagatesProviderRateLimitingWithoutRetryingAndLogsSanitizedProviderException(CapturedOutput output) {
        when(provider.submit("opaque-file-reference", SkinSimulationFocus.PORE))
                .thenThrow(new ProviderException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "PROVIDER_RATE_LIMITED",
                        "Rate limited; details at https://provider.example.test/error?token=private-value",
                        "25"));

        assertThatThrownBy(() -> service.simulate(ANALYSIS_ID, new SkinSimulationRequest("pore")))
                .isInstanceOfSatisfying(ProviderException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getCode()).isEqualTo("PROVIDER_RATE_LIMITED");
                    assertThat(exception.getRetryAfter()).isEqualTo("25");
                });
        String logs = output.getOut() + output.getErr();
        assertThat(logs)
                .contains("exceptionClass=ProviderException")
                .contains("providerErrorCode=PROVIDER_RATE_LIMITED")
                .contains("mappedHttpStatus=429")
                .contains("providerErrorMessage=Rate limited; details at [redacted-url]")
                .contains("focus=pore")
                .doesNotContain("provider.example.test", "private-value", "opaque-file-reference");
        verify(provider, never()).poll(any());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void keepsTheGeneric422ForAProviderFailedSnapshotAndLogsItsFocus(CapturedOutput output) {
        when(provider.submit("opaque-file-reference", SkinSimulationFocus.PORE))
                .thenReturn(new SkinSimulationSubmission("simulation-task"));
        when(provider.poll("simulation-task"))
                .thenReturn(new SkinSimulationSnapshot(AnalysisStatus.FAILED, null));

        assertThatThrownBy(() -> service.simulate(ANALYSIS_ID, new SkinSimulationRequest("pore")))
                .isInstanceOfSatisfying(SkinSimulationUnavailableException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getCode()).isEqualTo("SIMULATION_FAILED");
                    assertThat(exception.getMessage())
                            .isEqualTo("The visual simulation could not be completed. Please try again later.");
                });
        String logs = output.getOut() + output.getErr();
        assertThat(logs)
                .contains("provider task failed: provider=youcam, providerTaskStatus=FAILED, focus=pore")
                .doesNotContain("opaque-file-reference", "simulation-task");
    }

    @Test
    void stopsAfterTheConfiguredMaximumOfSixPollAttempts() {
        when(provider.submit("opaque-file-reference", SkinSimulationFocus.PORE))
                .thenReturn(new SkinSimulationSubmission("simulation-task"));
        when(provider.poll("simulation-task"))
                .thenReturn(new SkinSimulationSnapshot(AnalysisStatus.PROCESSING, null));

        assertThatThrownBy(() -> service.simulate(ANALYSIS_ID, new SkinSimulationRequest("pore")))
                .isInstanceOf(ProviderTimeoutException.class)
                .hasMessageContaining("polling window");
        verify(provider, org.mockito.Mockito.times(6)).poll("simulation-task");
    }

    @Test
    void mockProviderDoesNotRequireAYouCamFileReferenceAndReturnsStableData() {
        when(provider.providerName()).thenReturn("mock");
        when(provider.requiresSourceImageReference()).thenReturn(false);
        completedAnalysis.setOriginalImageReference(null);
        when(provider.submit(null, SkinSimulationFocus.ACNE)).thenReturn(new SkinSimulationSubmission("mock-task"));
        when(provider.poll("mock-task"))
                .thenReturn(new SkinSimulationSnapshot(AnalysisStatus.COMPLETED, "/mock-skin-simulation-after.svg"));

        var first = service.simulate(ANALYSIS_ID, new SkinSimulationRequest("acne"));
        var second = service.simulate(ANALYSIS_ID, new SkinSimulationRequest("acne"));

        assertThat(first).isEqualTo(second);
        assertThat(first.mock()).isTrue();
        assertThat(first.resultImageUrl()).isEqualTo("/mock-skin-simulation-after.svg");
    }

    private SkinConcern concern(String code) {
        return new SkinConcern(completedAnalysis, code, code, null, null, List.of());
    }
}
