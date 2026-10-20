package com.skincart.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SkinSimulationRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z_]+") String concernCode) {
}
