package adris.altoclef.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionSessionTest {

    @Test
    void tracksMovementAndCompletion() {
        CompanionSession session = new CompanionSession();
        session.startApproaching("Alex");
        assertEquals(CompanionState.APPROACHING_PLAYER, session.getState());
        assertEquals("Approaching Alex.", session.describe());
        session.completeMovement();
        assertEquals(CompanionState.IDLE, session.getState());
    }

    @Test
    void resumesTheStateThatWasPaused() {
        CompanionSession session = new CompanionSession();
        session.startReturningHome();
        session.pause();
        assertEquals(CompanionState.PAUSED, session.getState());
        session.resume();
        assertEquals(CompanionState.RETURNING_HOME, session.getState());
    }

    @Test
    void stopClearsTheCurrentTarget() {
        CompanionSession session = new CompanionSession();
        session.startFollowing("Alex");
        session.stop();
        assertEquals(CompanionState.STOPPED, session.getState());
        assertEquals("Stopped and waiting for a new request.", session.describe());
    }

    @Test
    void onlyCompletesOneShotMovementStates() {
        CompanionSession session = new CompanionSession();
        session.startFollowing("Alex");
        session.completeMovementIfActive();
        assertEquals(CompanionState.FOLLOWING, session.getState());

        session.startReturningHome();
        session.completeMovementIfActive();
        assertEquals(CompanionState.IDLE, session.getState());
    }

    @Test
    void safetyPauseIsNotOverwrittenByTaskCompletion() {
        CompanionSession session = new CompanionSession();
        session.startApproaching("Alex");
        session.safetyPause("lava detected");
        session.completeMovementIfActive();
        assertEquals(CompanionState.SAFETY_PAUSE, session.getState());
        assertEquals("Safety pause: lava detected", session.describe());
    }

    @Test
    void activeCompanionSessionHasOneOwner() {
        CompanionSession session = new CompanionSession();
        session.claimOwner("Alex");
        session.startFollowing("Alex");

        assertTrue(session.canControl("Alex"));
        assertFalse(session.canControl("Steve"));
        assertEquals("Following Alex. Session owner: Alex.", session.describe());

        session.stop();
        assertTrue(session.canControl("Steve"));
    }

    @Test
    void failedMovementRequestReleasesAnInactiveSessionOwner() {
        CompanionSession session = new CompanionSession();
        session.claimOwner("Alex");
        session.stop();
        session.claimOwner("Alex");
        session.releaseOwnerIfInactive();

        assertTrue(session.canControl("Steve"));
    }

    @Test
    void inactiveOwnerReleaseDoesNotOverridePausedOrSafetyStates() {
        CompanionSession session = new CompanionSession();
        session.claimOwner("Alex");
        session.startFollowing("Alex");
        session.pause();
        session.releaseOwnerIfInactive();
        assertFalse(session.canControl("Steve"));

        session.resume();
        session.safetyPause("lava detected");
        session.releaseOwnerIfInactive();
        assertFalse(session.canControl("Steve"));
    }
}
