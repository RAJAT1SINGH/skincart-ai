package com.skincart.provider;

import com.skincart.simulation.SkinSimulationFocus;

public interface SkinSimulationProvider {

    String providerName();

    boolean requiresSourceImageReference();

    SkinSimulationSubmission submit(String sourceImageReference, SkinSimulationFocus focus);

    SkinSimulationSnapshot poll(String taskId);
}
