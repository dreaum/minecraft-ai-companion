package adris.altoclef.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
