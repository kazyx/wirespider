/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.extension;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface PayloadFilter {
    /**
     * Called before the frame is created.
     *
     * @param data Original data. This might be modified after this method.
     * @return Filtered data.
     * @throws IOException Any filtering error detected or filtering is skipped.
     */
    ByteBuffer onSendingText(ByteBuffer data) throws IOException;

    /**
     * Called before the frame is created.
     *
     * @param data Original data. This might be modified after this method.
     * @return Filtered data.
     * @throws IOException Any filtering error detected or filtering is skipped.
     */
    ByteBuffer onSendingBinary(ByteBuffer data) throws IOException;

    /**
     * Called when the message is being restored and the registered reserved bits are {@code true}.
     *
     * @param data Original data. This might be modified after this method.
     * @return Filtered data.
     * @throws IOException Any filtering error detected.
     */
    ByteBuffer onReceivingText(ByteBuffer data) throws IOException;

    /**
     * Called when the message is being restored and the registered reserved bits are {@code true}.
     *
     * @param data Original data. This might be modified after this method.
     * @return Filtered data.
     * @throws IOException Any filtering error detected.
     */
    ByteBuffer onReceivingBinary(ByteBuffer data) throws IOException;
}
