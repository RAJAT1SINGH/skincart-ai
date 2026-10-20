package com.skincart.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.skincart.common.AnalysisStatus;
import com.skincart.entity.SkinAnalysis;
import com.skincart.entity.SkinConcern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class SkinAnalysisRepositoryTest {

    @Autowired
    private SkinAnalysisRepository repository;

    @Autowired
    private SkinConcernRepository concernRepository;

    @Test
    void persistsAndRetrievesAnalysisHistoryRecord() {
        SkinAnalysis analysis = new SkinAnalysis(Instant.parse("2026-01-01T00:00:00Z"), AnalysisStatus.CREATED);

        SkinAnalysis saved = repository.saveAndFlush(analysis);

        assertThat(repository.findById(saved.getId()))
                .get()
                .extracting(SkinAnalysis::getStatus)
                .isEqualTo(AnalysisStatus.CREATED);
    }

    @Test
    void persistsScoresAndMultipleConcernMaskUrls() {
        SkinAnalysis analysis = repository.saveAndFlush(
                new SkinAnalysis(Instant.parse("2026-01-01T00:00:00Z"), AnalysisStatus.COMPLETED));
        SkinConcern concern = new SkinConcern(
                analysis,
                "pore",
                "Pore",
                65,
                new BigDecimal("59.123456789"),
                List.of("https://mask.example/cheek.png", "https://mask.example/nose.png"));
        concernRepository.saveAndFlush(concern);

        SkinConcern saved = concernRepository.findAllByAnalysisIdWithMasks(analysis.getId()).getFirst();

        assertThat(saved.getUiScore()).isEqualTo(65);
        assertThat(saved.getRawScore()).isEqualByComparingTo("59.123456789");
        assertThat(saved.getMaskUrls())
                .containsExactly("https://mask.example/cheek.png", "https://mask.example/nose.png");
        assertThat(saved.getSeverity()).isNull();
    }
}
