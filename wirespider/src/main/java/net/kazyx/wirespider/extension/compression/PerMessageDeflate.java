/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.extension.compression;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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

    /**
     * @param strategy Strategy to be applied for this instance. If {@code null} is set, {@link #ALL_COMPRESSION_STRATEGY} is applied by default.
     */
    public PerMessageDeflate(CompressionStrategy strategy) {
        if (strategy == null) {
            strategy = ALL_COMPRESSION_STRATEGY;
        }
        mStrategy = strategy;
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

    private final Deflater mCompressor = new Deflater(Deflater.BEST_COMPRESSION, true);
    private final ByteArrayOutputStream mCompressionBuffer = new ByteArrayOutputStream();
    private static final int DEFLATE_BUFFER = 512;

    @Override
    public byte[] compress(byte[] source) throws IOException {
        if (source.length < mStrategy.minSizeInBytes()) {
            return source;
        }

        synchronized (mCompressor) {
            mCompressor.reset();
            mCompressionBuffer.reset();

            DeflaterOutputStream dos = new DeflaterOutputStream(mCompressionBuffer, mCompressor, DEFLATE_BUFFER);
            OutputStream os = new BufferedOutputStream(dos);
            os.write(source);
            os.flush();
            dos.finish();

            return mCompressionBuffer.toByteArray();
        }
    }

    private final Inflater mDecompressor = new Inflater(true);
    private final ByteArrayOutputStream mDecompressionBuffer = new ByteArrayOutputStream();
    private static final int INFLATE_BUFFER = 512;

    @Override
    public byte[] decompress(byte[] source) throws IOException {
        synchronized (mDecompressor) {
            mDecompressor.reset();
            mDecompressionBuffer.reset();

            InflaterOutputStream ios = new InflaterOutputStream(mDecompressionBuffer, mDecompressor, INFLATE_BUFFER);
            OutputStream os = new BufferedOutputStream(ios);
            os.write(source, 0, source.length);
            os.flush();
            ios.finish();

            return mDecompressionBuffer.toByteArray();
        }
    }
}
