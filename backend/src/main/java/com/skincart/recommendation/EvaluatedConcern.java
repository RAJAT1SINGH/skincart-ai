package com.skincart.recommendation;

public record EvaluatedConcern(String code, String displayName, Integer uiScore, double priorityWeight) {
}
