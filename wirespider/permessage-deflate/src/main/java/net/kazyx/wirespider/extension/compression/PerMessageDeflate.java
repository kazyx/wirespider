/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.extension.compression;

import net.kazyx.wirespider.extension.PayloadFilter;
import net.kazyx.wirespider.util.BinaryUtil;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

/**
 * permessage-deflate extension
 */
public class PerMessageDeflate extends PerMessageCompression {
    /**
     * 8. permessage-deflate extension
     */
    public static final String NAME = "permessage-deflate";
    /**
     * @see <a href="https://tools.ietf.org/html/rfc7692#section-7.1.1.1">RFC 7692 Section 7.1.1.1</a>
     */
    static final String SERVER_NO_CONTEXT_TAKEOVER = "server_no_context_takeover";

    /**
     * @see <a href="https://tools.ietf.org/html/rfc7692#section-7.1.1.2">RFC 7692 Section 7.1.1.2</a>
     */
    static final String CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover";

    static final String SERVER_MAX_WINDOW_BITS = "server_max_window_bits";

    // static final String CLIENT_MAX_WINDOW_BITS = "client_max_window_bits";

    private int mCompressionThreshold;

    private final DeflateFilter mFilter;

    /**
     * @param threshold Minimum size of messages to enable compression in bytes.
     */
    PerMessageDeflate(int threshold) {
        mCompressionThreshold = threshold;
        mFilter = new DeflateFilter(this);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean accept(String[] parameters) {
        List<String> list = Arrays.asList(parameters);
        return list.containsAll(Arrays.asList(CLIENT_NO_CONTEXT_TAKEOVER, SERVER_NO_CONTEXT_TAKEOVER));
    }

    @Override
    public PayloadFilter filter() {
        return mFilter;
    }

    private final Deflater mCompressor = new Deflater(Deflater.BEST_COMPRESSION, true);
    private static final int DEFLATE_BUFFER = 512;

    private static final IOException MESSAGE_TOO_SMALL = new IOException("Avoid deflate for small message");

    @Override
    public ByteBuffer compress(ByteBuffer source) throws IOException {
        if (source.remaining() < mCompressionThreshold) {
            throw MESSAGE_TOO_SMALL;
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(source.remaining());

        synchronized (mCompressor) {
            mCompressor.reset();

            DeflaterOutputStream dos = new DeflaterOutputStream(buffer, mCompressor, DEFLATE_BUFFER);
            OutputStream os = new BufferedOutputStream(dos);
            os.write(BinaryUtil.toBytesRemaining(source));
            os.flush();
            dos.finish();

            return ByteBuffer.wrap(buffer.toByteArray());
        }
    }

    private final Inflater mDecompressor = new Inflater(true);
    private static final int INFLATE_BUFFER = 512;

    @Override
    public ByteBuffer decompress(ByteBuffer source) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(source.remaining());

        synchronized (mDecompressor) {
            mDecompressor.reset();

            InflaterOutputStream ios = new InflaterOutputStream(buffer, mDecompressor, INFLATE_BUFFER);
            OutputStream os = new BufferedOutputStream(ios);
            os.write(source.array(), 0, source.remaining());
            os.flush();
            ios.finish();

            return ByteBuffer.wrap(buffer.toByteArray());
        }
    }
}
