/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.extension.PayloadFilter;

public interface FrameTx {
    void sendTextAsync(String data);

    void sendBinaryAsync(byte[] data);

    void sendPingAsync(String message);

    void sendPongAsync(String message);

    void sendCloseAsync(CloseStatusCode code, String reason);

    void setPayloadFilter(PayloadFilter filter);
}
