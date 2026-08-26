package adris.altoclef.companion;

/** The externally visible state of the companion, independent of pathfinding details. */
public enum CompanionState {
    IDLE,
    FOLLOWING,
    APPROACHING_PLAYER,
    RETURNING_HOME,
    PAUSED,
    STOPPED
}
