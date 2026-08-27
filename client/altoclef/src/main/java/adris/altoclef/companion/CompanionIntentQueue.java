package adris.altoclef.companion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Priority queue with stable FIFO ordering for intents at the same priority. */
final class CompanionIntentQueue {

    private static final Comparator<Entry> ORDER = Comparator
            .comparingInt((Entry queued) -> queued.intent().priority()).reversed()
            .thenComparingLong(Entry::sequence);

    private final List<Entry> entries = new ArrayList<>();
    private long nextSequence;

    Entry enqueue(CompanionIntent intent, String owner, Consumer<String> reply) {
        Entry entry = new Entry(intent, owner, reply, nextSequence++);
        entries.add(entry);
        entries.sort(ORDER);
        return entry;
    }

    void requeue(Entry entry) {
        entries.add(entry);
        entries.sort(ORDER);
    }

    Entry peek() {
        return entries.isEmpty() ? null : entries.get(0);
    }

    Entry poll() {
        return entries.isEmpty() ? null : entries.remove(0);
    }

    boolean removeType(CompanionIntent.Type type) {
        return entries.removeIf(entry -> entry.intent().type() == type);
    }

    void clear() {
        entries.clear();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    List<Entry> snapshot() {
        return List.copyOf(entries);
    }

    record Entry(CompanionIntent intent, String owner, Consumer<String> reply, long sequence) {
    }
}
