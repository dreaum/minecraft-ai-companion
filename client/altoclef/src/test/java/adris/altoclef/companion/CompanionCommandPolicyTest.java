package adris.altoclef.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionCommandPolicyTest {

    @Test
    void permitsCompanionCommandsRegardlessOfCaseOrSpacing() {
        assertTrue(CompanionCommandPolicy.isAllowed("follow"));
        assertTrue(CompanionCommandPolicy.isAllowed("  STATUS  "));
        assertTrue(CompanionCommandPolicy.isAllowed("pause now"));
    }

    @Test
    void rejectsEmptyAndWorldModifyingCommands() {
        assertFalse(CompanionCommandPolicy.isAllowed(null));
        assertFalse(CompanionCommandPolicy.isAllowed("  "));
        assertFalse(CompanionCommandPolicy.isAllowed("get diamond 64"));
        assertFalse(CompanionCommandPolicy.isAllowed("goto 0 64 0"));
        assertFalse(CompanionCommandPolicy.isAllowed("give Steve diamond 1"));
    }
}
