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
import net.kazyx.wirespider.util.SelectionKeyUtil;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class SocketEngine {
    private static final String TAG = SocketEngine.class.getSimpleName();

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
                        final SelectionKey key = itr.next();
                        itr.remove();
                        if (key.isValid()) {
                            final WebSocket ws = (WebSocket) key.attachment();
                            if (key.isConnectable()) {
                                try {
                                    SocketChannel ch = (SocketChannel) key.channel();
                                    if (ch.finishConnect()) {
                                        String scheme = ws.remoteUri().getScheme().toLowerCase(Locale.US);
                                        SessionFactory factory = mFactories.get(scheme);
                                        if (factory == null) {
                                            factory = mDefaultFactory;
                                        }
                                        SelectionKeyUtil.interestOps(key, SelectionKey.OP_READ);
                                        final Session session = factory.createNew(key);
                                        session.setListener(new Session.Listener() {
                                            @Override
                                            public void onAppDataReceived(LinkedList<byte[]> data) {
                                                ws.socketChannelProxy().onReceived(data);
                                            }

                                            @Override
                                            public void onConnected() {
                                                ws.socketChannelProxy().onConnected(session);
                                            }
                                        });
                                        mSessionMap.put(ch, session);
                                        continue;
                                    }
                                } catch (IOException e) {
                                    WsLog.printStackTrace(TAG, e);
                                    // Fallthrough
                                }
                                ws.socketChannelProxy().onConnectionFailed();
                            } else {
                                SocketChannel ch = (SocketChannel) key.channel();
                                Session session = mSessionMap.get(ch);
                                if (session != null) {
                                    onSelected(key, ws.socketChannelProxy());
                                }
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

    /**
     * Called only when the connection is already established.
     *
     * @param key Key of the selected channel.
     */
    private void onSelected(SelectionKey key, SocketChannelProxy proxy) {
        try {
            if (!key.isValid()) {
                WsLog.d(TAG, "Skip invalid key");
                return;
            }
            if (key.isReadable()) {
                onReadReady(key);
            }
            if (key.isWritable()) {
                onWriteReady(key);
            }
        } catch (IOException | CancelledKeyException e) {
            proxy.onClosed();
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

    private void onReadReady(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();

        Session session = mSessionMap.get(ch);
        if (session == null) {
            throw new IOException("Cannot read from closed session");
        }

        session.onReadReady();
    }

    void onWriteReady(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();

        Session session = mSessionMap.get(ch);
        if (session == null) {
            throw new IOException("Cannot read from closed session");
        }

        session.onFlushReady();
    }

    /**
     * @param factory {@link SessionFactory}to use for the specified URI scheme.
     * @param scheme Scheme to use the {@link SessionFactory} to create a {@link Session}
     */
    void registerFactory(SessionFactory factory, String scheme) {
        mFactories.put(scheme.toLowerCase(Locale.US), factory);
    }
}
