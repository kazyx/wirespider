package net.kazyx.wirespider.extension;

import java.io.IOException;

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
    byte[] onReceivingText(byte[] data, byte extensionBits) throws IOException;

    /**
     * Called before the message is restored from the frame.
     *
     * @param data Original data. This might be modified after this method.
     * @param extensionBits Reserved bits of the frame header.
     * @return Filtered data.
     * @throws IOException Any filtering error detected.
     */
    byte[] onReceivingBinary(byte[] data, byte extensionBits) throws IOException;
}
