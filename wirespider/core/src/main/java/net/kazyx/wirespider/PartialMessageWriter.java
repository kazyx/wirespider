/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import java.io.Closeable;
import java.io.IOException;

public class PartialMessageWriter implements Closeable {
    private FrameTx mTx;
    private boolean mIsFirst = true;
    private boolean mIsOpen = true;

    /**
     * @param tx Frame transmitter.
     * @throws IllegalStateException If another {@link PartialMessageWriter} is derived from given {@link FrameTx} is not closed.
     */
    PartialMessageWriter(FrameTx tx) {
        mTx = tx;
        mTx.lock();
    }

    /**
     * @param data Partial data of binary message
     * @param isFinal Final part of the partial message or not.
     * @throws IOException PartialMessageWriter is closed
     */
    public void sendPartialFrameAsync(byte[] data, boolean isFinal) throws IOException {
        if (!mIsOpen) {
            throw new IOException("PartialMessageWriter is closed");
        }
        if (mIsFirst) {
            mTx.sendBinaryAsync(data, isFinal);
            mIsFirst = false;
        } else {
            mTx.sendContinuationAsync(data, isFinal);
        }
        if (isFinal) {
            mTx.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        if (!mIsOpen) {
            throw new IOException("PartialMessageWriter is closed");
        }

        try {
            mTx.unlock();
            mIsOpen = false;
        } catch (IllegalMonitorStateException e) {
            throw new IOException(e);
        }
    }
}
