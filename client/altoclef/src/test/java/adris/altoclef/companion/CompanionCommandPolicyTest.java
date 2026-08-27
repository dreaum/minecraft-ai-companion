package adris.altoclef.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionCommandPolicyTest {

    @Test
    void permitsApprovedCompanionCommandsRegardlessOfCaseOrOuterSpacing() {
        assertTrue(CompanionCommandPolicy.isAllowed("follow"));
        assertTrue(CompanionCommandPolicy.isAllowed("come"));
        assertTrue(CompanionCommandPolicy.isAllowed("home"));
        assertTrue(CompanionCommandPolicy.isAllowed("  STATUS  "));
        assertTrue(CompanionCommandPolicy.isAllowed("collect iron_ingot 64"));
        assertTrue(CompanionCommandPolicy.isAllowed("goto 0 64 0"));
    }

    @Test
    void rejectsEmptyMalformedAndChainedCommands() {
        assertFalse(CompanionCommandPolicy.isAllowed(null));
        assertFalse(CompanionCommandPolicy.isAllowed("  "));
        assertFalse(CompanionCommandPolicy.isAllowed("get diamond 64"));
        assertFalse(CompanionCommandPolicy.isAllowed("goto 0 64"));
        assertFalse(CompanionCommandPolicy.isAllowed("give Steve diamond 1"));
        assertFalse(CompanionCommandPolicy.isAllowed("pause now"));
        assertFalse(CompanionCommandPolicy.isAllowed("follow; get diamond 64"));
        assertFalse(CompanionCommandPolicy.isAllowed("follow ; get diamond 64"));
    }

    @Test
    void identifiesCommandsThatStartMovement() {
        assertTrue(CompanionCommandPolicy.startsMovement("follow"));
        assertTrue(CompanionCommandPolicy.startsMovement("come"));
        assertTrue(CompanionCommandPolicy.startsMovement("home"));
        assertFalse(CompanionCommandPolicy.startsMovement("status"));
        assertFalse(CompanionCommandPolicy.startsMovement("stop"));
    }

    @Test
    void onlyStatusIsReadOnlyForTheActiveSession() {
        assertFalse(CompanionCommandPolicy.requiresSessionOwnership("status"));
        assertTrue(CompanionCommandPolicy.requiresSessionOwnership("follow"));
        assertFalse(CompanionCommandPolicy.requiresSessionOwnership("queue"));
        assertTrue(CompanionCommandPolicy.requiresSessionOwnership("stop"));
    }
}
