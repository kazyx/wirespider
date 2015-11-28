/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.util.IOUtil;

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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class SocketEngine {
    private static final String TAG = SocketEngine.class.getSimpleName();

    private static final int DIRECT_BUFFER_SIZE = 1024 * 4;

    private final ByteBuffer mReadBuffer = ByteBuffer.allocateDirect(DIRECT_BUFFER_SIZE);

    private final Map<SocketChannel, Session> mSessionMap = new ConcurrentHashMap<>();

    private final Map<String, SessionFactory> mFactories = new ConcurrentHashMap<>();

    private final SessionFactory mDefaultFactory = new DefaultSessionFactory();

    SocketEngine(SelectorProvider provider) throws IOException {
        mSelectorThread = new SelectorThread(provider.openSelector());
        mSelectorThread.start();
    }

    void destroy() {
        mSelectorThread.interrupt();
    }

    private class SelectorThread extends Thread {
        private final Selector mSelector;

        SelectorThread(Selector selector) {
            super("ws-selector");
            mSelector = selector;
        }

        @Override
        public void run() {
            // Log.d(TAG, "SelectorThread started");
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
                            WebSocket ws = (WebSocket) key.attachment();
                            if (key.isConnectable()) {
                                try {
                                    SocketChannel ch = (SocketChannel) key.channel();
                                    if (ch.finishConnect()) {
                                        String scheme = ws.remoteUri().getScheme().toLowerCase(Locale.US);
                                        SessionFactory factory = mFactories.get(scheme);
                                        if (factory == null) {
                                            factory = mDefaultFactory;
                                        }
                                        Session session = factory.createNew(ch);
                                        mSessionMap.put(ch, session);
                                        key.interestOps(SelectionKey.OP_READ);
                                        ws.socketChannelProxy().onConnected(key);
                                        continue;
                                    }
                                } catch (IOException e) {
                                    // Fallthrough
                                }
                                ws.socketChannelProxy().onConnectionFailed();
                            } else {
                                ws.socketChannelProxy().onSelected(key);
                            }
                        } else {
                            SocketChannel ch = (SocketChannel) key.channel();
                            Session session = mSessionMap.get(ch);
                            if (session != null) {
                                IOUtil.close(session);
                                mSessionMap.remove(ch);
                            }
                        }
                    }
                }
                WsLog.d(TAG, "Select Loop finished");
            } finally {
                for (SelectionKey key : mSelector.keys()) {
                    key.cancel();
                    IOUtil.close(key.channel());
                    ((WebSocket) key.attachment()).socketChannelProxy().onCancelled();
                }
                mQueue.clear();
                IOUtil.close(mSelector);
            }
        }

        private final List<Runnable> mQueue = new ArrayList<>();

        private boolean select() {
            try {
                mSelector.select();
                //Log.d(TAG, "selected: " + selected);
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
                WsLog.printStackTrace(TAG, e);
                return false;
            }
        }

        void registerNewChannel(final SocketChannel channel, final int ops, final WebSocket ws) {
            synchronized (mQueue) {
                mQueue.add(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            channel.register(mSelector, ops, ws);
                        } catch (ClosedChannelException e) {
                            WsLog.printStackTrace(TAG, e);
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
     * @param ws WebSocket to be registered.
     * @param ops Selector operations.
     */
    public void register(WebSocket ws, int ops) {
        mSelectorThread.registerNewChannel(ws.socketChannel(), ops, ws);
    }

    byte[] read(SocketChannel ch) throws IOException {
        Session session = mSessionMap.get(ch);
        if (session == null) {
            throw new IOException("Cannot read from closed session");
        }
        try {
            int length = session.read(mReadBuffer);
            if (length == -1) {
                throw new IOException("EOF");
            } else if (length == 0) {
                return null;
            } else {
                mReadBuffer.flip();
                byte[] ret = new byte[length];
                mReadBuffer.get(ret);
                return ret;
            }
        } finally {
            mReadBuffer.clear();
        }
    }

    /**
     * Write given {@link ByteBuffer} to the SocketChannel.
     *
     * @param ch SocketChannel to write data.
     * @param bb ByteBuffer to be written.
     * @throws IOException If some I/O error occurs.
     */
    void write(SocketChannel ch, ByteBuffer bb) throws IOException {
        Session session = mSessionMap.get(ch);
        if (session == null) {
            throw new IOException("Cannot write into closed session");
        }
        session.write(bb);
    }

    /**
     * @param factory {@link SessionFactory}to use for the specified URI scheme.
     * @param scheme Scheme to use the {@link SessionFactory} to create a {@link Session}
     */
    void registerFactory(SessionFactory factory, String scheme) {
        mFactories.put(scheme.toLowerCase(Locale.US), factory);
    }
}
