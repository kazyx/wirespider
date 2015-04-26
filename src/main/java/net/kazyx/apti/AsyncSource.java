package net.kazyx.apti;

import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

class AsyncSource {
    /**
     * Release all of thread resources.
     */
    void destroy() {
        mConnectionThreadPool.shutdown();
        mTimer.purge();
    }

    AsyncSource(ExecutorService executor) {
        mReceiverThreadPool = executor;
    }

    final ExecutorService mConnectionThreadPool = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread("apti-connection-thread");
        }
    });

    private final ExecutorService mReceiverThreadPool;

    void safeAsyncAction(Runnable task) {
        try {
            mReceiverThreadPool.submit(task);
        } catch (RejectedExecutionException e) {
            // Nothing to do.
        }
    }

    final Timer mTimer = new Timer("apti-timer");
}
