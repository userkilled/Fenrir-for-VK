package com.github.luben.zstd;

import java.nio.ByteBuffer;

public class ZstdDictDecompress extends SharedDictBase {

    private long nativePtr;

    private native void init(byte[] dict, int dict_offset, int dict_size);

    private native void initDirect(ByteBuffer dict, int dict_offset, int dict_size);

    private native void free();

    /**
     * Convenience constructor to create a new dictionary for use with fast decompress
     *
     * @param dict buffer containing dictionary to load/parse with exact length
     */
    public ZstdDictDecompress(byte[] dict) {
        this(dict, 0, dict.length);
    }

    /**
     * Create a new dictionary for use with fast decompress
     *
     * @param dict   buffer containing dictionary
     * @param offset the offset into the buffer to read from
     * @param length number of bytes to use from the buffer
     */
    public ZstdDictDecompress(byte[] dict, int offset, int length) {

        init(dict, offset, length);

        if (nativePtr == 0L) {
           throw new IllegalStateException("ZSTD_createDDict failed");
        }
        // Ensures that even if ZstdDictDecompress is created and published through a race, no thread could observe
        // nativePtr == 0.
        storeFence();
    }


    /**
     * Create a new dictionary for use with fast decompress. The provided bytebuffer is available for reuse when the method returns.
     *
     * @param dict   Direct ByteBuffer containing dictionary using position and limit to define range in buffer.
     */
    public ZstdDictDecompress(ByteBuffer dict) {

        int length = dict.limit() - dict.position();
        if (!dict.isDirect()) {
            throw new IllegalArgumentException("dict must be a direct buffer");
        }
        if (length < 0) {
            throw new IllegalArgumentException("dict cannot be empty.");
        }
        initDirect(dict, dict.position(), length);

        if (nativePtr == 0L) {
            throw new IllegalStateException("ZSTD_createDDict failed");
        }
        // Ensures that even if ZstdDictDecompress is created and published through a race, no thread could observe
        // nativePtr == 0.
        storeFence();
    }

    @Override
     void doClose() {
        if (nativePtr != 0) {
            free();
            nativePtr = 0;
        }
    }
}
