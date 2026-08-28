package adris.altoclef.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionSafetyRulesTest {

    @Test
    void ignoresEverythingWhenNoCompanionMovementIsActive() {
        assertTrue(CompanionSafetyRules.evaluate(false, true, 1, false, -2).isEmpty());
    }

    @Test
    void neverPausesForEnvironmentHazardsOrLowHealthOrHunger() {
        assertTrue(CompanionSafetyRules.evaluate(true, true, true,
                6, 3, true, 20, 300, false, true, 0).isEmpty());
    }

    @Test
    void pausesForDangerousFall() {
        assertEquals("dangerous fall detected",
                CompanionSafetyRules.evaluate(true, false, 20, false, -0.8).orElseThrow());
    }

    @Test
    void pausesForSuffocation() {
        assertEquals("suffocation detected", CompanionSafetyRules.evaluate(true, false, false,
                20, 20, false, 300, 300, true, true, 0).orElseThrow());
    }
}
