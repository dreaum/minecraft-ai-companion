package adris.altoclef.companion;

import java.util.Objects;

/** Stores companion-facing intent while AltoClef remains responsible for task execution. */
public final class CompanionSession {

    private CompanionState state = CompanionState.IDLE;
    private String targetPlayer;
    private String owner;
    private CompanionState pausedState = CompanionState.IDLE;
    private String safetyReason;

    public CompanionState getState() {
        return state;
    }

    public boolean canControl(String playerName) {
        return owner == null || owner.equalsIgnoreCase(playerName);
    }

    public void claimOwner(String playerName) {
        if (!canControl(playerName)) {
            throw new IllegalStateException("A different player owns the active companion session.");
        }
        owner = Objects.requireNonNull(playerName, "playerName");
    }

    public void releaseOwnerIfIdle() {
        if (state == CompanionState.IDLE) {
            owner = null;
        }
    }

    public void startFollowing(String playerName) {
        targetPlayer = Objects.requireNonNull(playerName, "playerName");
        safetyReason = null;
        state = CompanionState.FOLLOWING;
    }

    public void startApproaching(String playerName) {
        targetPlayer = Objects.requireNonNull(playerName, "playerName");
        safetyReason = null;
        state = CompanionState.APPROACHING_PLAYER;
    }

    public void startReturningHome() {
        targetPlayer = null;
        safetyReason = null;
        state = CompanionState.RETURNING_HOME;
    }

    public void completeMovement() {
        targetPlayer = null;
        owner = null;
        state = CompanionState.IDLE;
    }

    public void completeMovementIfActive() {
        if (state == CompanionState.APPROACHING_PLAYER || state == CompanionState.RETURNING_HOME) {
            completeMovement();
        }
    }

    public boolean isMovementActive() {
        return state == CompanionState.FOLLOWING
                || state == CompanionState.APPROACHING_PLAYER
                || state == CompanionState.RETURNING_HOME;
    }

    public void safetyPause(String reason) {
        targetPlayer = null;
        safetyReason = Objects.requireNonNull(reason, "reason");
        state = CompanionState.SAFETY_PAUSE;
    }

    public void pause() {
        if (state != CompanionState.PAUSED) {
            pausedState = state;
            state = CompanionState.PAUSED;
        }
    }

    public void resume() {
        if (state == CompanionState.PAUSED) {
            state = pausedState;
        }
    }

    public void stop() {
        targetPlayer = null;
        owner = null;
        pausedState = CompanionState.IDLE;
        safetyReason = null;
        state = CompanionState.STOPPED;
    }

    public String describe() {
        String activity = switch (state) {
            case FOLLOWING -> "Following " + targetPlayer + ".";
            case APPROACHING_PLAYER -> "Approaching " + targetPlayer + ".";
            case RETURNING_HOME -> "Returning home.";
            case PAUSED -> "Paused.";
            case SAFETY_PAUSE -> "Safety pause: " + safetyReason;
            case STOPPED -> "Stopped and waiting for a new request.";
            case IDLE -> "Idle and ready.";
        };
        return owner == null ? activity : activity + " Session owner: " + owner + ".";
    }
}
