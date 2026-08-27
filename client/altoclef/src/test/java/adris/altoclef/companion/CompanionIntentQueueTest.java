package adris.altoclef.companion;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionIntentQueueTest {

    private static final Consumer<String> NO_REPLY = ignored -> { };

    @Test
    void givesProtectThenMovementThenResourcesPriorityWithFifoTies() {
        CompanionIntentQueue queue = new CompanionIntentQueue();
        queue.enqueue(CompanionIntent.target(CompanionIntent.Type.COLLECT, "iron_ingot", 1), "owner", NO_REPLY);
        queue.enqueue(CompanionIntent.simple(CompanionIntent.Type.FOLLOW), "owner", NO_REPLY);
        queue.enqueue(CompanionIntent.target(CompanionIntent.Type.CRAFT, "torch", 4), "owner", NO_REPLY);
        queue.enqueue(CompanionIntent.simple(CompanionIntent.Type.PROTECT), "owner", NO_REPLY);

        assertEquals(CompanionIntent.Type.PROTECT, queue.poll().intent().type());
        assertEquals(CompanionIntent.Type.FOLLOW, queue.poll().intent().type());
        assertEquals(CompanionIntent.Type.COLLECT, queue.poll().intent().type());
        assertEquals(CompanionIntent.Type.CRAFT, queue.poll().intent().type());
        assertNull(queue.poll());
    }

    @Test
    void preservesSequenceWhenAnInterruptedIntentIsRequeued() {
        CompanionIntentQueue queue = new CompanionIntentQueue();
        CompanionIntentQueue.Entry collect = queue.enqueue(
                CompanionIntent.target(CompanionIntent.Type.COLLECT, "iron_ingot", 1), "owner", NO_REPLY);
        assertEquals(CompanionIntent.Type.COLLECT, queue.poll().intent().type());
        queue.enqueue(CompanionIntent.target(CompanionIntent.Type.CRAFT, "torch", 4), "owner", NO_REPLY);
        queue.requeue(collect);

        assertEquals(CompanionIntent.Type.COLLECT, queue.poll().intent().type());
        assertEquals(CompanionIntent.Type.CRAFT, queue.poll().intent().type());
    }

    @Test
    void removesProtectionAndClearsPendingIntents() {
        CompanionIntentQueue queue = new CompanionIntentQueue();
        queue.enqueue(CompanionIntent.simple(CompanionIntent.Type.PROTECT), "owner", NO_REPLY);
        queue.enqueue(CompanionIntent.target(CompanionIntent.Type.COLLECT, "iron_ingot", 1), "owner", NO_REPLY);

        assertTrue(queue.removeType(CompanionIntent.Type.PROTECT));
        assertFalse(queue.removeType(CompanionIntent.Type.PROTECT));
        assertEquals(CompanionIntent.Type.COLLECT, queue.peek().intent().type());
        queue.clear();
        assertTrue(queue.isEmpty());
    }
}
