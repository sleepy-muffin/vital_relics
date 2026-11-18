package com.example.vitalrelics.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The multiplier is chosen so that the effort per level (experience needed
 * divided by the multiplier) interpolates between the vanilla cost and the
 * flat first-level cost; these tests pin that behaviour down.
 */
class ExperienceConvergenceTest {

    private static final int VANILLA_NEED = 100;

    @Test
    void zeroLevelLeavesGainUntouched() {
        assertEquals(1.0, MyEvents.experienceConvergenceMultiplier(VANILLA_NEED, 0.0));
    }

    @Test
    void fullLevelFlattensEveryLevelToTheFirst() {
        final double multiplier = MyEvents.experienceConvergenceMultiplier(VANILLA_NEED, 1.0);
        assertEquals(7.0, VANILLA_NEED / multiplier, 1e-9);
    }

    @Test
    void halfLevelDoesExactlyHalfTheExtraWork() {
        final double effort = VANILLA_NEED
                / MyEvents.experienceConvergenceMultiplier(VANILLA_NEED, 0.5);

        // Half way between the vanilla cost and the flat converged cost, i.e.
        // exactly half of the *extra* work above the converged floor.
        assertEquals((VANILLA_NEED + 7.0) / 2.0, effort, 1e-9);
    }

    @Test
    void effortIsLinearInLevel() {
        final double flat = VANILLA_NEED
                / MyEvents.experienceConvergenceMultiplier(VANILLA_NEED, 1.0);
        final double quarter = VANILLA_NEED
                / MyEvents.experienceConvergenceMultiplier(VANILLA_NEED, 0.25);
        final double threeQuarters = VANILLA_NEED
                / MyEvents.experienceConvergenceMultiplier(VANILLA_NEED, 0.75);

        assertEquals((3.0 * VANILLA_NEED + flat) / 4.0, quarter, 1e-9);
        assertEquals((VANILLA_NEED + 3.0 * flat) / 4.0, threeQuarters, 1e-9);
    }

    @Test
    void multiplierGrowsMonotonicallyWithLevel() {
        double previous = 0.0;

        for (int step = 0; step <= 10; step++) {
            final double multiplier = MyEvents.experienceConvergenceMultiplier(VANILLA_NEED, step / 10.0);
            assertTrue(multiplier >= previous, "multiplier should never fall as level rises");
            previous = multiplier;
        }
    }

    @Test
    void neverReducesGainWhenTheNextLevelIsAlreadyCheap() {
        // Below level 1 the vanilla cost can be smaller than 7, which would make
        // the ratio (and the raw power) drop under 1; the gain must not shrink.
        for (int need = 1; need <= 7; need++) {
            assertTrue(MyEvents.experienceConvergenceMultiplier(need, 1.0) >= 1.0);
        }
    }

    @Test
    void halfStrengthIsMeaningfullySlowerThanFullStrength() {
        final double half = VANILLA_NEED / MyEvents.experienceConvergenceMultiplier(VANILLA_NEED, 0.5);
        final double full = VANILLA_NEED / MyEvents.experienceConvergenceMultiplier(VANILLA_NEED, 1.0);

        assertEquals(2.0 * half - full, 100.0,
                "half strength should cost far more than full strength");
    }
}
