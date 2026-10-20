package com.skincart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skincart.common.AnalysisStatus;
import com.skincart.entity.SkinAnalysis;
import com.skincart.exception.InvalidImageException;
import com.skincart.exception.ResourceNotFoundException;
import com.skincart.mapper.SkinAnalysisMapper;
import com.skincart.provider.ProviderException;
import com.skincart.provider.ProviderTimeoutException;
import com.skincart.provider.SkinAnalysisImage;
import com.skincart.provider.SkinAnalysisProvider;
import com.skincart.provider.SkinAnalysisResult;
import com.skincart.provider.SkinAnalysisSnapshot;
import com.skincart.provider.SkinAnalysisSubmission;
import com.skincart.provider.SkinConcernResult;
import com.skincart.repository.SkinAnalysisRepository;
import com.skincart.repository.SkinConcernRepository;
import java.math.BigDecimal;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class SkinAnalysisServiceTest {

    @Mock
    private SkinAnalysisRepository skinAnalysisRepository;

    @Mock
    private SkinConcernRepository skinConcernRepository;

    @Mock
    private ImageUploadValidator imageUploadValidator;

    @Mock
    private SkinAnalysisProvider skinAnalysisProvider;

    private SkinAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new SkinAnalysisService(
                skinAnalysisRepository,
                skinConcernRepository,
                new SkinAnalysisMapper(),
                imageUploadValidator,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                skinAnalysisProvider,
                Duration.ZERO,
                3);
        lenient().when(skinAnalysisRepository.save(any(SkinAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(skinAnalysisProvider.providerName()).thenReturn("test-provider");
    }

    @Test
    void createsAnalysisUploadsImagePollsAndPersistsMappedResult() {
        MockMultipartFile image = image();
        when(skinAnalysisProvider.submit(any(SkinAnalysisImage.class)))
                .thenReturn(new SkinAnalysisSubmission("provider-task-1", "opaque-file-reference"));
        when(skinAnalysisProvider.poll("provider-task-1"))
                .thenReturn(new SkinAnalysisSnapshot(AnalysisStatus.PROCESSING, null))
                .thenReturn(new SkinAnalysisSnapshot(
                        AnalysisStatus.COMPLETED,
                        new SkinAnalysisResult(
                                null,
                                List.of(new SkinConcernResult(
                                        "wrinkle", 78, new BigDecimal("71.23456789"), List.of("https://mask.example/mask.png"))))));

        var response = service.create(image);

        assertThat(response.status()).isEqualTo(AnalysisStatus.COMPLETED);
        org.mockito.ArgumentCaptor<SkinAnalysis> savedAnalysis = org.mockito.ArgumentCaptor.forClass(SkinAnalysis.class);
        verify(skinAnalysisRepository, org.mockito.Mockito.atLeastOnce()).save(savedAnalysis.capture());
        assertThat(savedAnalysis.getAllValues())
                .anySatisfy(saved -> assertThat(saved.getOriginalImageReference()).isEqualTo("opaque-file-reference"));
        verify(imageUploadValidator).validate(image);
        verify(skinAnalysisProvider).submit(any(SkinAnalysisImage.class));
        verify(skinAnalysisProvider, org.mockito.Mockito.times(2)).poll("provider-task-1");
        verify(skinConcernRepository).saveAll(any());
    }

    @Test
    void mapsProviderTaskFailureToInternalFailedState() {
        when(skinAnalysisProvider.submit(any(SkinAnalysisImage.class)))
                .thenReturn(new SkinAnalysisSubmission("provider-task-2"));
        when(skinAnalysisProvider.poll("provider-task-2"))
                .thenReturn(new SkinAnalysisSnapshot(AnalysisStatus.FAILED, null));

        var response = service.create(image());

        assertThat(response.status()).isEqualTo(AnalysisStatus.FAILED);
        verify(skinConcernRepository, never()).saveAll(any());
    }

    @Test
    void marksAnalysisFailedAndRaisesTimeoutAfterBoundedPolling() {
        when(skinAnalysisProvider.submit(any(SkinAnalysisImage.class)))
                .thenReturn(new SkinAnalysisSubmission("provider-task-3"));
        when(skinAnalysisProvider.poll("provider-task-3"))
                .thenReturn(new SkinAnalysisSnapshot(AnalysisStatus.PROCESSING, null));

        assertThatThrownBy(() -> service.create(image())).isInstanceOf(ProviderTimeoutException.class);

        verify(skinAnalysisProvider, org.mockito.Mockito.times(3)).poll("provider-task-3");
        verify(skinAnalysisRepository, org.mockito.Mockito.atLeast(3)).save(any(SkinAnalysis.class));
    }

    @Test
    void rejectsPollingConfigurationAboveSixAttempts() {
        assertThatThrownBy(() -> new SkinAnalysisService(
                        skinAnalysisRepository,
                        skinConcernRepository,
                        new SkinAnalysisMapper(),
                        imageUploadValidator,
                        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                        skinAnalysisProvider,
                        Duration.ofSeconds(10),
                        7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 6");
    }

    @Test
    void persistsProviderFailureBeforePropagatingSafeApplicationError() {
        when(skinAnalysisProvider.submit(any(SkinAnalysisImage.class)))
                .thenThrow(new ProviderException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "PROVIDER_UNAVAILABLE",
                        "The skin analysis provider is temporarily unavailable."));

        assertThatThrownBy(() -> service.create(image()))
                .isInstanceOf(ProviderException.class)
                .hasMessage("The skin analysis provider is temporarily unavailable.");

        verify(skinAnalysisRepository, org.mockito.Mockito.atLeast(2)).save(any(SkinAnalysis.class));
        verify(skinAnalysisProvider, never()).poll(any());
    }

    @Test
    void doesNotCallProviderForInvalidImage() {
        doThrow(new InvalidImageException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "Invalid image."))
                .when(imageUploadValidator)
                .validate(any());

        assertThatThrownBy(() -> service.create(image())).isInstanceOf(InvalidImageException.class);

        verify(skinAnalysisProvider, never()).submit(any());
        verify(skinAnalysisRepository, never()).save(any());
    }

    @Test
    void readsAnalysisAndItsConcerns() {
        UUID analysisId = UUID.randomUUID();
        SkinAnalysis analysis = new SkinAnalysis(Instant.parse("2026-01-01T00:00:00Z"), AnalysisStatus.CREATED);
        when(skinAnalysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(skinConcernRepository.findAllByAnalysisIdWithMasks(analysisId)).thenReturn(List.of());

        var response = service.get(analysisId);

        assertThat(response.status()).isEqualTo(AnalysisStatus.CREATED);
        assertThat(response.concerns()).isEmpty();
        verify(skinConcernRepository).findAllByAnalysisIdWithMasks(analysisId);
    }

    @Test
    void rejectsUnknownAnalysisId() {
        UUID analysisId = UUID.randomUUID();
        when(skinAnalysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(analysisId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Skin analysis was not found.");
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "selfie.png", "image/png", new byte[] {1, 2, 3});
    }
}
