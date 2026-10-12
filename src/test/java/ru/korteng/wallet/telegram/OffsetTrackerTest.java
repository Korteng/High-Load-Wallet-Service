package ru.korteng.wallet.telegram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffsetTrackerTest {

    private final OffsetTracker tracker = new OffsetTracker();

    @Test
    void initialOffsetIsZero() {
        assertEquals(0, tracker.nextOffset());
    }

    @Test
    void afterHandlingTheOffsetMovesPastTheUpdate() {
        assertTrue(tracker.register(10, true));
        tracker.done(10);
        assertEquals(11, tracker.nextOffset());
    }

    @Test
    void unfinishedUpdatePinsTheOffset_evenWhenLaterOnesAreDone() {
        tracker.register(10, true);
        tracker.register(11, true);
        tracker.done(11);
        // 10 is still being handled: Telegram must keep it, otherwise a crash would lose it
        assertEquals(10, tracker.nextOffset());
        tracker.done(10);
        assertEquals(12, tracker.nextOffset());
    }

    @Test
    void redeliveredUpdatesAreRejected() {
        assertTrue(tracker.register(10, true));
        assertFalse(tracker.register(10, true));
        assertFalse(tracker.register(9, true));
        assertTrue(tracker.register(11, true));
    }

    @Test
    void updatesWithoutHandlingAdvanceTheOffsetButAreNotPinned() {
        assertTrue(tracker.register(5, false));
        assertEquals(6, tracker.nextOffset());
    }
}
