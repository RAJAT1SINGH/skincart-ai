package com.skincart.recommendation;

import com.skincart.entity.SkinConcern;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ConcernEvaluator {

    private static final double MISSING_SCORE_PRIORITY = 50.0;

    public List<EvaluatedConcern> evaluate(List<SkinConcern> concerns) {
        List<EvaluatedConcern> evaluated = concerns.stream()
                .map(concern -> new EvaluatedConcern(
                        concern.getConcernCode().toLowerCase(Locale.ROOT),
                        concern.getDisplayName(),
                        concern.getUiScore(),
                        priorityFor(concern.getUiScore())))
                .sorted(Comparator.comparingDouble(EvaluatedConcern::priorityWeight)
                        .reversed()
                        .thenComparing(EvaluatedConcern::code))
                .toList();

        if (!evaluated.isEmpty() && evaluated.stream().allMatch(concern -> concern.priorityWeight() == 0.0)) {
            return evaluated.stream()
                    .map(concern -> new EvaluatedConcern(
                            concern.code(), concern.displayName(), concern.uiScore(), 1.0))
                    .toList();
        }
        return evaluated;
    }

    private double priorityFor(Integer uiScore) {
        if (uiScore == null) {
            return MISSING_SCORE_PRIORITY;
        }
        int normalizedScore = Math.clamp(uiScore, 0, 100);
        return 100.0 - normalizedScore;
    }
}
