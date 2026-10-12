package ru.korteng.wallet.telegram;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs tasks on a shared pool, but tasks with the same key (chat id) strictly one after another, in submission
 * order. Different keys run in parallel, so a slow LLM call in one chat does not block the others.
 */
final class PerChatExecutor {

    private final ExecutorService pool;
    private final Map<Long, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    PerChatExecutor(int threads) {
        AtomicInteger counter = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "telegram-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    void submit(long key, Runnable task) {
        CompletableFuture<Void> next = tails.compute(key, (k, previous) -> previous == null
                // handleAsync, not thenRunAsync: a failed earlier task must not cancel the later ones
                ? CompletableFuture.runAsync(task, pool)
                : previous.handleAsync((result, error) -> {
                    task.run();
                    return null;
                }, pool));
        // drop the entry once this task is the last one for the chat, so the map does not grow forever
        next.whenComplete((result, error) -> tails.remove(key, next));
    }

    void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
