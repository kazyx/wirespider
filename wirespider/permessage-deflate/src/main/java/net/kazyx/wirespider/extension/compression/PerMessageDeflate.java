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
     * @see <a href="https://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-21#section-8.1.1.1">Section 8.1.1.1</a>
     */
    public static final String SERVER_NO_CONTEXT_TAKEOVER = "server_no_context_takeover";

    /**
     * @see <a href="https://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-21#section-8.1.1.2">Section 8.1.1.2</a>
     */
    public static final String CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover";

    /**
     * @see <a href="https://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-21#section-8.1.2.1">Section 8.1.2.1</a>
     */
    public static final String SERVER_MAX_WINDOW_BITS = "server_max_window_bits";

    /**
     * @see <a href="https://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-21#section-8.1.2.2">Section 8.1.2.2</a>
     */
    public static final String CLIENT_MAX_WINDOW_BITS = "client_max_window_bits";

    private static final CompressionStrategy ALL_COMPRESSION_STRATEGY = new CompressionStrategy() {
        @Override
        public int minSizeInBytes() {
            return 0; // Try compression for any data
        }
    };

    private final CompressionStrategy mStrategy;

    private final DeflateFilter mFilter;

    /**
     * @param strategy Strategy to be applied for this instance. If {@code null} is set, {@link #ALL_COMPRESSION_STRATEGY} is applied by default.
     */
    public PerMessageDeflate(CompressionStrategy strategy) {
        if (strategy == null) {
            strategy = ALL_COMPRESSION_STRATEGY;
        }
        mStrategy = strategy;
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

    @Override
    public byte[] compress(byte[] source) throws IOException {
        if (source.length < mStrategy.minSizeInBytes()) {
            return source;
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(source.length);

        synchronized (mCompressor) {
            mCompressor.reset();

            DeflaterOutputStream dos = new DeflaterOutputStream(buffer, mCompressor, DEFLATE_BUFFER);
            OutputStream os = new BufferedOutputStream(dos);
            os.write(source);
            os.flush();
            dos.finish();

            return buffer.toByteArray();
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
