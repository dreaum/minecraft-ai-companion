package adris.altoclef.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionSafetyRulesTest {

    @Test
    void ignoresDangerWhenNoCompanionMovementIsActive() {
        assertTrue(CompanionSafetyRules.evaluate(false, true, 1, false, -2).isEmpty());
    }

    @Test
    void pausesForLavaBeforeOtherConditions() {
        assertEquals("lava detected", CompanionSafetyRules.evaluate(true, true, 1, false, -2).orElseThrow());
    }

    @Test
    void pausesForLowHealthAndFalls() {
        assertEquals("health is critically low", CompanionSafetyRules.evaluate(true, false, 6, true, 0).orElseThrow());
        assertEquals("dangerous fall detected", CompanionSafetyRules.evaluate(true, false, 20, false, -0.8).orElseThrow());
    }

    @Test
    void pausesForFireDrowningSuffocationAndHunger() {
        assertEquals("fire detected", CompanionSafetyRules.evaluate(true, false, true,
                20, 20, false, 300, 300, false, true, 0).orElseThrow());
        assertEquals("drowning risk detected", CompanionSafetyRules.evaluate(true, false, false,
                20, 20, true, 20, 300, false, true, 0).orElseThrow());
        assertEquals("suffocation detected", CompanionSafetyRules.evaluate(true, false, false,
                20, 20, false, 300, 300, true, true, 0).orElseThrow());
        assertEquals("hunger is critically low", CompanionSafetyRules.evaluate(true, false, false,
                20, 3, false, 300, 300, false, true, 0).orElseThrow());
    }
}
