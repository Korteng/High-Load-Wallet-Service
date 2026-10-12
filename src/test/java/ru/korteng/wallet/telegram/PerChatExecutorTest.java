package ru.korteng.wallet.telegram;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerChatExecutorTest {

    private final PerChatExecutor executor = new PerChatExecutor(4);

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void sameChat_runsInSubmissionOrder_evenIfTheFirstTaskIsSlow() throws Exception {
        List<Integer> order = Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch done = new CountDownLatch(3);
        executor.submit(1L, () -> {
            sleep(200);
            order.add(1);
            done.countDown();
        });
        executor.submit(1L, () -> {
            order.add(2);
            done.countDown();
        });
        executor.submit(1L, () -> {
            order.add(3);
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void differentChats_doNotBlockEachOther() throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch fastDone = new CountDownLatch(1);
        executor.submit(1L, () -> {
            slowStarted.countDown();
            await(release);
        });
        assertTrue(slowStarted.await(5, TimeUnit.SECONDS));
        executor.submit(2L, fastDone::countDown);
        // the other chat finishes while chat 1 is still blocked
        assertTrue(fastDone.await(5, TimeUnit.SECONDS));
        release.countDown();
    }

    @Test
    void failedTask_doesNotStopLaterTasksOfTheSameChat() throws Exception {
        CountDownLatch next = new CountDownLatch(1);
        executor.submit(1L, () -> {
            throw new IllegalStateException("boom");
        });
        executor.submit(1L, next::countDown);
        assertTrue(next.await(5, TimeUnit.SECONDS));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
