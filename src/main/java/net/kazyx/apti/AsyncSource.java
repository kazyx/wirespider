package net.kazyx.apti;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

public class AsyncSource {
    /**
     * Release all of thread resources.
     */
    public void destroy() {
        mActionThreadPool.shutdown();
        mConnectionThreadPool.shutdown();
        mTimer.purge();
    }

    final Thread mSelectorThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                Selector selector = SelectorProvider.provider().openSelector();
                while (selector.select() > 0) {
                    for (SelectionKey key : selector.selectedKeys()) {
                        SelectionHandler handler = (SelectionHandler) key.attachment();
                        handler.onSelected(key);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    });

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
