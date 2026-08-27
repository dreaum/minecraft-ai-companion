package adris.altoclef.companion;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Serializes approved companion intents and is the only private-message path to user tasks. */
public final class CompanionOrchestrator {

    private final AltoClef mod;
    private final CompanionSession session;
    private final List<QueuedIntent> queue = new ArrayList<>();
    private long nextSequence;
    private QueuedIntent active;
    private long ignoredCompletion = -1;

    public CompanionOrchestrator(AltoClef mod, CompanionSession session) {
        this.mod = mod;
        this.session = session;
    }

    public void handle(String sender, String message, Consumer<String> reply) {
        CompanionIntentParser.ParseResult parsed = CompanionIntentParser.parse(message);
        if (!parsed.accepted()) {
            reply.accept("Rejected: " + parsed.error());
            return;
        }
        CompanionIntent intent = parsed.intent().orElseThrow();
        if (!session.canControl(sender)) {
            reply.accept("Rejected: another player owns the active companion session.");
            return;
        }
        if (intent.type() == CompanionIntent.Type.STATUS) {
            reply.accept(session.describe() + " " + queueDescription());
            return;
        }
        if (intent.type() == CompanionIntent.Type.QUEUE) {
            reply.accept(queueDescription());
            return;
        }

        String validationError = CompanionTaskFactory.validate(intent);
        if (validationError != null) {
            reply.accept("Rejected: " + validationError);
            return;
        }
        session.claimOwner(sender);
        if (intent.type() == CompanionIntent.Type.STOP) {
            stop(reply);
            return;
        }
        if (intent.type() == CompanionIntent.Type.UNPROTECT) {
            unprotect(reply);
            return;
        }

        QueuedIntent queued = new QueuedIntent(intent, sender, reply, nextSequence++);
        queue.add(queued);
        queue.sort(QueuedIntent.ORDER);
        reply.accept("Queued: " + intent.describe() + ". " + queueDescription());
        preemptIfNeeded();
        startNext();
    }

    public boolean isActive() {
        return active != null || !queue.isEmpty();
    }

    /** Preserves the interrupted intent for a later resume after AltoClef's survival chains take over. */
    public void safetyPause(String reason) {
        if (active == null) {
            session.safetyPause(reason);
            return;
        }
        QueuedIntent interrupted = active;
        active = null;
        ignoredCompletion = interrupted.sequence;
        queue.add(interrupted);
        queue.sort(QueuedIntent.ORDER);
        session.safetyPause(reason);
        interrupted.reply.accept("Paused for safety: " + reason + ". " + queueDescription());
        mod.cancelUserTask();
    }

    public void resumeAfterSafety() {
        if (session.getState() != CompanionState.SAFETY_PAUSE) {
            return;
        }
        session.setIdle();
        startNext();
    }

    private void stop(Consumer<String> reply) {
        queue.clear();
        if (active != null) {
            ignoredCompletion = active.sequence;
            active = null;
            mod.cancelUserTask();
        }
        session.stop();
        reply.accept("Stopped. The task queue was cleared.");
    }

    private void unprotect(Consumer<String> reply) {
        boolean removed = queue.removeIf(queued -> queued.intent.type() == CompanionIntent.Type.PROTECT);
        if (active != null && active.intent.type() == CompanionIntent.Type.PROTECT) {
            ignoredCompletion = active.sequence;
            active = null;
            mod.cancelUserTask();
            removed = true;
        }
        if (removed) {
            session.setIdle();
            reply.accept("Protection disabled.");
        } else {
            reply.accept("Protection was not active.");
        }
        startNext();
    }

    private void preemptIfNeeded() {
        if (active == null || queue.isEmpty()) {
            return;
        }
        QueuedIntent next = queue.get(0);
        if (next.intent.priority() <= active.intent.priority()) {
            return;
        }
        QueuedIntent interrupted = active;
        active = null;
        ignoredCompletion = interrupted.sequence;
        queue.add(interrupted);
        queue.sort(QueuedIntent.ORDER);
        mod.cancelUserTask();
    }

    private void startNext() {
        if (active != null || queue.isEmpty()) {
            return;
        }
        active = queue.remove(0);
        QueuedIntent running = active;
        try {
            Task task = CompanionTaskFactory.create(mod, running.intent, running.owner);
            updateSessionFor(running.intent, running.owner);
            running.reply.accept("Executing: " + running.intent.describe() + ".");
            mod.runUserTask(task, () -> finished(running.sequence));
        } catch (RuntimeException exception) {
            active = null;
            running.reply.accept("TASK FAILED: " + exception.getMessage());
            startNext();
        }
    }

    private void finished(long sequence) {
        if (sequence == ignoredCompletion) {
            ignoredCompletion = -1;
            return;
        }
        if (active == null || active.sequence != sequence) {
            return;
        }
        QueuedIntent completed = active;
        active = null;
        if (completed.intent.type() != CompanionIntent.Type.PROTECT) {
            completed.reply.accept("Finished: " + completed.intent.describe() + ".");
        }
        if (session.getState() == CompanionState.SAFETY_PAUSE) {
            completed.reply.accept("Paused: " + session.describe());
            return;
        }
        session.setIdle();
        startNext();
        if (!isActive()) {
            session.releaseOwnerIfInactive();
        }
    }

    private void updateSessionFor(CompanionIntent intent, String owner) {
        switch (intent.type()) {
            case FOLLOW -> session.startFollowing(owner);
            case COME -> session.startApproaching(owner);
            case HOME -> session.startReturningHome();
            case PROTECT -> session.startProtecting(owner);
            default -> session.startExecuting(intent.describe());
        }
    }

    private String queueDescription() {
        if (active == null && queue.isEmpty()) {
            return "Queue: empty.";
        }
        StringBuilder result = new StringBuilder("Queue:");
        if (active != null) {
            result.append(" running ").append(active.intent.describe()).append(';');
        }
        for (QueuedIntent intent : queue) {
            result.append(' ').append(intent.intent.describe()).append(';');
        }
        return result.toString();
    }

    private record QueuedIntent(CompanionIntent intent, String owner, Consumer<String> reply, long sequence) {
        private static final Comparator<QueuedIntent> ORDER = Comparator
                .comparingInt((QueuedIntent queued) -> queued.intent.priority()).reversed()
                .thenComparingLong(QueuedIntent::sequence);
    }
}
