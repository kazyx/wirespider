/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.extension.Extension;

import java.util.List;

public interface FrameTx {
    /**
     * Send non-partial TEXT data frame <b>under the rule of lock state</b>.
     *
     * @param data Application data.
     * @throws IllegalStateException If lock is held somewhere.
     */
    void sendTextAsync(String data);

    /**
     * Send TEXT data frame <b>regardless of lock state</b>.
     *
     * @param data Application data.
     * @param continuation {@code false} if this frame is the leading frame of the partial messages.
     * @param isFinal {@code false} if this is the initial part of partial message, otherwise {true}.
     */
    void sendTextAsyncPrivileged(String data, boolean continuation, boolean isFinal);

    /**
     * Send non-partial BINARY data frame <b>under the rule of lock state</b>.
     *
     * @param data Application data.
     * @throws IllegalStateException If lock is held somewhere.
     */
    void sendBinaryAsync(byte[] data);

    /**
     * Send BINARY data frame <b>regardless of lock state</b>.
     *
     * @param data Application data.
     * @param continuation {@code false} if this frame is the leading frame of the partial messages.
     * @param isFinal {@code false} if this is the initial part of partial message, otherwise {true}.
     */
    void sendBinaryAsyncPrivileged(byte[] data, boolean continuation, boolean isFinal);

    /**
     * Send PING frame for keep-alive or check of peer's activity.
     *
     * @param message PING message.
     */
    void sendPingAsync(String message);

    /**
     * Send PONG frame as a response for PING message.
     *
     * @param message message received with PING frame.
     */
    void sendPongAsync(String message);

    /**
     * Send CLOSE frame before closing connection.
     *
     * @param code WebSocket status code
     * @param reason Close reason. This might be {@code null}.
     */
    void sendCloseAsync(CloseStatusCode code, String reason);

    /**
     * Set WebSocket extensions to be used on this session.
     *
     * @param extensions Negotiated extensions.
     */
    void setExtensions(List<Extension> extensions);

    /**
     * Lock data frame operations until {@link #unlock()} is called.
     *
     * @throws IllegalStateException Another lock is not cleared.
     */
    void lock();

    /**
     * Unlock data frame operations.
     *
     * @throws IllegalMonitorStateException If this thread is not holding lock.
     */
    void unlock();
}
