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
     * @param extensionBits Reserved bits of the frame header can be updated with the 0th element of this.
     * @return Filtered data.
     * @throws IOException Any filtering error detected.
     */
    byte[] onSendingText(byte[] data, byte[] extensionBits) throws IOException;

    /**
     * Called before the frame is created.
     *
     * @param data Original data. This might be modified after this method.
     * @param extensionBits Reserved bits of the frame header can be updated with the 0th element of this.
     * @return Filtered data.
     * @throws IOException Any filtering error detected.
     */
    byte[] onSendingBinary(byte[] data, byte[] extensionBits) throws IOException;

    /**
     * Called before the message is restored from the frame.
     *
     * @param data Original data. This might be modified after this method.
     * @param extensionBits Reserved bits of the frame header.
     * @return Filtered data.
     * @throws IOException Any filtering error detected.
     */
    ByteBuffer onReceivingText(ByteBuffer data, byte extensionBits) throws IOException;

    /**
     * Called before the message is restored from the frame.
     *
     * @param data Original data. This might be modified after this method.
     * @param extensionBits Reserved bits of the frame header.
     * @return Filtered data.
     * @throws IOException Any filtering error detected.
     */
    ByteBuffer onReceivingBinary(ByteBuffer data, byte extensionBits) throws IOException;
}
