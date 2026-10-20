package com.skincart.simulation;

import java.util.Arrays;
import java.util.Optional;

/** SkinCart concern codes that have a documented direct YouCam simulation effect. */
public enum SkinSimulationFocus {
    ACNE("acne", "Acne appearance"),
    WRINKLE("wrinkle", "Wrinkle appearance"),
    PORE("pore", "Pore appearance"),
    TEXTURE("texture", "Skin texture");

    private final String concernCode;
    private final String displayName;

    SkinSimulationFocus(String concernCode, String displayName) {
        this.concernCode = concernCode;
        this.displayName = displayName;
    }

    public String concernCode() {
        return concernCode;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<SkinSimulationFocus> forConcernCode(String concernCode) {
        return Arrays.stream(values())
                .filter(focus -> focus.concernCode.equals(concernCode))
                .findFirst();
    }
}
