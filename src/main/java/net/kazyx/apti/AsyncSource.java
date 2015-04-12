package net.kazyx.apti;

import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

public class AsyncSource {
    public void destroy() {
        mActionThreadPool.shutdown();
        mConnectionThreadPool.shutdown();
        mTimer.purge();
    }

    /**
     * @param poolSize Size of thread pool for various use.
     */
    AsyncSource(int poolSize) {
        mActionThreadPool = Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread("apti-action-thread");
            }
        });
    }

    final ExecutorService mConnectionThreadPool = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread("apti-connection-thread");
        }
    });

    final ExecutorService mActionThreadPool;

    void safeAsyncAction(Runnable task) {
        try {
            mActionThreadPool.submit(task);
        } catch (RejectedExecutionException e) {
            // Nothing to do.
        }
    }

    final Timer mTimer = new Timer("apti-timer");
}
