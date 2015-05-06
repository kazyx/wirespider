package net.kazyx.apti;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

class AsyncSource {
    private static final String TAG = AsyncSource.class.getSimpleName();

    private static final int DIRECT_BUFFER_SIZE = 4096;

    /**
     * Release all of thread resources.
     */
    synchronized void destroy() {
        AptiLog.d(TAG, "destroy");
        mConnectionThreadPool.shutdown();
        mScheduler.shutdownNow();
        mSelectorThread.interrupt();
    }

    AsyncSource(SelectorProvider provider) throws IOException {
        mSelectorThread = new SelectorThread(provider.openSelector());
        mSelectorThread.start();
    }

    final ExecutorService mConnectionThreadPool = Executors.newCachedThreadPool();

    /**
     * Asynchronously invoke task on cached thread pool if it is available.
     *
     * @param task Runnable to be invoked.
     */
    void safeAsync(Runnable task) {
        try {
            mConnectionThreadPool.submit(task);
        } catch (RejectedExecutionException e) {
            AptiLog.d(TAG, "RejectedExecution");
        }
    }

    final ScheduledExecutorService mScheduler = Executors.newSingleThreadScheduledExecutor();

    private final ByteBuffer mByteBuffer = ByteBuffer.allocateDirect(DIRECT_BUFFER_SIZE);

    static class SelectorThread extends Thread {
        private final Selector mSelector;

        SelectorThread(Selector selector) {
            super("apti-selector-thread");
            mSelector = selector;
        }

        @Override
        public void run() {
            // AptiLog.d(TAG, "SelectorThread started");
            try {
                while (true) {
                    if (!select()) {
                        break;
                    }
                    Iterator<SelectionKey> itr = mSelector.selectedKeys().iterator();
                    while (itr.hasNext()) {
                        SelectionKey key = itr.next();
                        itr.remove();
                        if (key.isValid()) {
                            ((SocketChannelProxy) key.attachment()).onSelected(key);
                        }
                    }
                }
                AptiLog.d(TAG, "Select Loop finished");
            } finally {
                for (SelectionKey key : mSelector.keys()) {
                    key.cancel();
                    IOUtil.close(key.channel());
                    ((SocketChannelProxy) key.attachment()).onCancelled();
                }
                mQueue.clear();
                IOUtil.close(mSelector);
            }
        }

        private final List<Runnable> mQueue = new ArrayList<>();

        private boolean select() {
            try {
                mSelector.select();
                //AptiLog.d(TAG, "selected: " + selected);
                if (this.isInterrupted()) {
                    return false;
                }
                synchronized (mQueue) {
                    Iterator<Runnable> itr = mQueue.iterator();
                    while (itr.hasNext()) {
                        itr.next().run();
                        itr.remove();
                    }
                }
                return true;
            } catch (IOException e) {
                AptiLog.printStackTrace(TAG, e);
                return false;
            }
        }

        void registerNewChannel(final SocketChannel channel, final int ops, final SocketChannelProxy handler) {
            synchronized (mQueue) {
                mQueue.add(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            channel.register(mSelector, ops, handler);
                        } catch (ClosedChannelException e) {
                            AptiLog.printStackTrace(TAG, e);
                        }
                    }
                });
            }
            mSelector.wakeup();
        }
    }

    private final SelectorThread mSelectorThread;

    /**
     * Register new WebSocket to the Selector.
     *
     * @param ws  WebSocket to e registered.
     * @param ops Selector operations.
     */
    void register(WebSocket ws, int ops) {
        mSelectorThread.registerNewChannel(ws.socketChannel(), ops, ws.socketChannelProxy());
    }

    byte[] read(SocketChannel ch) throws IOException {
        try {
            int length = ch.read(mByteBuffer);
            if (length == -1) {
                throw new IOException("EOF");
            } else if (length == 0) {
                return null;
            } else {
                mByteBuffer.flip();
                byte[] ret = new byte[length];
                mByteBuffer.get(ret);
                return ret;
            }
        } finally {
            mByteBuffer.clear();
        }
    }
}
