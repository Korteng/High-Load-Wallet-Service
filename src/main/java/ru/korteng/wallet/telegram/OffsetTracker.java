package ru.korteng.wallet.telegram;

import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Decides which offset is sent to Telegram's getUpdates. Telegram deletes every update below the offset, so the
 * offset must not pass an update that is still being handled: otherwise a crash would lose that message.
 * Unfinished updates are redelivered by Telegram until the offset moves past them; {@link #register} filters
 * the ones that were already dispatched.
 */
final class OffsetTracker {

    private static final long NONE = -1;

    private final ConcurrentSkipListSet<Long> inFlight = new ConcurrentSkipListSet<>();
    private long lastSeen = NONE; // read and written only by the polling thread

    /**
     * Registers an update fetched from Telegram.
     *
     * @param needsHandling true if it is handed to a worker (and must be marked {@link #done} later)
     * @return false if the update was already seen, i.e. it is a redelivery and must be skipped
     */
    boolean register(long updateId, boolean needsHandling) {
        if (updateId <= lastSeen) {
            return false;
        }
        lastSeen = updateId;
        if (needsHandling) {
            inFlight.add(updateId);
        }
        return true;
    }

    /** Called by a worker when the update has been handled (or failed for good). */
    void done(long updateId) {
        inFlight.remove(updateId);
    }

    /** The oldest unfinished update, or the one after the last seen update when nothing is in flight. */
    long nextOffset() {
        try {
            return inFlight.first();
        } catch (NoSuchElementException e) {
            return lastSeen == NONE ? 0 : lastSeen + 1;
        }
    }
}
