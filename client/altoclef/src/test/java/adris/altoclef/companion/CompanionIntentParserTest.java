package adris.altoclef.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionIntentParserTest {

    @Test
    void parsesStrictResourceAndMovementCommands() {
        CompanionIntent collect = CompanionIntentParser.parse("collect iron_ingot 64").intent().orElseThrow();
        assertEquals(CompanionIntent.Type.COLLECT, collect.type());
        assertEquals("iron_ingot", collect.target());
        assertEquals(64, collect.count());

        CompanionIntent gotoIntent = CompanionIntentParser.parse("goto -10 64 25").intent().orElseThrow();
        assertEquals(CompanionIntent.Type.GOTO, gotoIntent.type());
        assertEquals(-10, gotoIntent.x());
        assertEquals(64, gotoIntent.y());
        assertEquals(25, gotoIntent.z());
    }

    @Test
    void rejectsChainingMalformedTargetsAndCountsOutsideLimit() {
        assertFalse(CompanionIntentParser.parse("collect iron_ingot 65").accepted());
        assertFalse(CompanionIntentParser.parse("give diamond 0").accepted());
        assertFalse(CompanionIntentParser.parse("collect iron ingot 1").accepted());
        assertFalse(CompanionIntentParser.parse("follow; collect diamond 1").accepted());
        assertFalse(CompanionIntentParser.parse("goto 0 64").accepted());
    }

    @Test
    void givesProtectAndMovementTheirSchedulingPriorities() {
        assertTrue(CompanionIntent.simple(CompanionIntent.Type.PROTECT).priority()
                > CompanionIntent.simple(CompanionIntent.Type.FOLLOW).priority());
        assertTrue(CompanionIntent.simple(CompanionIntent.Type.FOLLOW).priority()
                > CompanionIntent.target(CompanionIntent.Type.COLLECT, "iron_ingot", 1).priority());
        assertTrue(CompanionIntent.simple(CompanionIntent.Type.STATUS).isReadOnly());
    }
}
