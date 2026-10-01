package com.slabbed.compat.sable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SableLightSamplingTest {
    @Test
    void settledObjectAboveLoweredStoneUsesExposedLightAtNegativeWorldHeight() {
        assertTrue(SableLightSampling.aboveLoweredTop(-58.003d, -59, 0.5d, -0.5d));
    }

    @Test
    void actualInteriorAndFlushStoneKeepTheirShadow() {
        assertFalse(SableLightSampling.aboveLoweredTop(-58.75d, -59, 0.5d, -0.5d));
        assertFalse(SableLightSampling.aboveLoweredTop(-58.003d, -59, 1.0d, 0.0d));
    }

    @Test
    void contactBoundaryUsesTheShiftedOcclusionTop() {
        assertTrue(SableLightSampling.aboveLoweredTop(10.5d, 10, 0.5d, -0.5d));
        assertFalse(SableLightSampling.aboveLoweredTop(10.499d, 10, 0.5d, -0.5d));
        assertTrue(SableLightSampling.aboveLoweredTop(10.25d, 10, 0.0d, -0.5d));
    }
}
