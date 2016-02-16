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
    private FrameType mDataType;
    private FrameTx mTx;
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
     * Send partial text frames.<br>
     * Don't forget to {@link #close()} after final frame is sent.
     *
     * @param data Partial data of text message
     * @param isFinal Final part of the partial message or not.
     * @throws IOException PartialMessageWriter is closed
     * @throws IllegalStateException Used for a data type other than text frame.
     */
    public void sendPartialFrameAsync(String data, boolean isFinal) throws IOException {
        if (mDataType != null && mDataType != FrameType.TEXT) {
            throw new IllegalStateException("Already used for other data type: " + mDataType);
        }
        if (!mIsOpen) {
            throw new IOException("PartialMessageWriter is closed");
        }
        if (mDataType == null) {
            mTx.sendTextAsyncPrivileged(data, false, isFinal);
            mDataType = FrameType.TEXT;
        } else {
            mTx.sendTextAsyncPrivileged(data, true, isFinal);
        }
        if (isFinal) {
            mDataType = null;
        }
    }

    /**
     * Send partial binary frames.<br>
     * Don't forget to {@link #close()} after final frame is sent.
     *
     * @param data Partial data of binary message
     * @param isFinal Final part of the partial message or not.
     * @throws IOException PartialMessageWriter is closed
     * @throws IllegalStateException Used for a data type other than binary frame.
     */
    public void sendPartialFrameAsync(byte[] data, boolean isFinal) throws IOException {
        if (mDataType != null && mDataType != FrameType.BINARY) {
            throw new IllegalStateException("Already used for other data type: " + mDataType);
        }
        if (!mIsOpen) {
            throw new IOException("PartialMessageWriter is closed");
        }
        if (mDataType == null) {
            mTx.sendBinaryAsyncPrivileged(data, false, isFinal);
            mDataType = FrameType.BINARY;
        } else {
            mTx.sendBinaryAsyncPrivileged(data, true, isFinal);
        }
        if (isFinal) {
            mDataType = null;
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
