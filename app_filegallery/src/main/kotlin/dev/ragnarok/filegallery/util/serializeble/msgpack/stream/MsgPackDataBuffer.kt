package dev.ragnarok.filegallery.util.serializeble.msgpack.stream

import okio.BufferedSource

interface MsgPackDataBuffer {
    fun toByteArray(): ByteArray
}

abstract class MsgPackDataOutputBuffer : MsgPackDataBuffer {
    abstract fun add(byte: Byte): Boolean
    abstract fun addAll(bytes: List<Byte>): Boolean
    abstract fun addAll(bytes: ByteArray): Boolean
}

abstract class MsgPackDataInputBuffer : MsgPackDataBuffer {
    abstract fun skip(bytes: Int)
    abstract fun peek(): Byte
    abstract fun peekSafely(): Byte?

    // Increases index only if next byte is not null
    abstract fun nextByteOrNull(): Byte?
    abstract fun requireNextByte(): Byte
    abstract fun takeNext(next: Int): ByteArray
    abstract fun currentIndex(): Int
}

class MsgPackDataOutputArrayBuffer : MsgPackDataOutputBuffer() {
    private val bytes = mutableListOf<Byte>()
    override fun add(byte: Byte) = bytes.add(byte)
    override fun addAll(bytes: List<Byte>) = this.bytes.addAll(bytes)
    override fun addAll(bytes: ByteArray) = this.bytes.addAll(bytes.toList())
    override fun toByteArray() = bytes.toByteArray()
}

class MsgPackDataInputArrayBuffer(private val byteArray: ByteArray) : MsgPackDataInputBuffer() {
    private var index = 0
    override fun skip(bytes: Int) {
        require(bytes > 0) { "Number of bytes to take must be greater than 0!" }
        index += bytes
    }

    override fun currentIndex(): Int {
        return index
    }

    override fun peek(): Byte = byteArray.getOrNull(index) ?: throw Exception("End of stream")
    override fun peekSafely(): Byte? = byteArray.getOrNull(index)

    // Increases index only if next byte is not null
    override fun nextByteOrNull(): Byte? = byteArray.getOrNull(index)?.also { index++ }
    override fun requireNextByte(): Byte = nextByteOrNull() ?: throw Exception("End of stream")
    override fun takeNext(next: Int): ByteArray {
        require(next > 0) { "Number of bytes to take must be greater than 0!" }
        val result = ByteArray(next)
        (0 until next).forEach {
            result[it] = requireNextByte()
        }
        return result
    }

    override fun toByteArray(): ByteArray {
        return byteArray
    }
}

class MsgPackDataInputOkio(private val bufferedSource: BufferedSource) : MsgPackDataInputBuffer() {
    private var index = 0
    private var preloaded: Byte = 0x00
    private var isPreloaded = false
    override fun skip(bytes: Int) {
        require(bytes > 0) { "Number of bytes to take must be greater than 0!" }
        bufferedSource.skip(bytes.toLong() - 1)
        index += bytes - 1
        isPreloaded = false
    }

    override fun currentIndex(): Int {
        return index
    }

    override fun peek(): Byte {
        if (!isPreloaded) {
            isPreloaded = true
            preloaded = (bufferedSource.readByte().toInt() and 0xff).toByte()
            index++
        }
        return preloaded
    }

    override fun peekSafely(): Byte? {
        return try {
            peek()
        } catch (e: Exception) {
            null
        }
    }

    // Increases index only if next byte is not null
    override fun nextByteOrNull(): Byte? = peekSafely()?.also { isPreloaded = false }
    override fun requireNextByte(): Byte = nextByteOrNull() ?: throw Exception("End of stream")
    override fun takeNext(next: Int): ByteArray {
        require(next > 0) { "Number of bytes to take must be greater than 0!" }
        val result = ByteArray(next)
        (0 until next).forEach {
            result[it] = requireNextByte()
        }
        return result
    }

    override fun toByteArray(): ByteArray {
        throw UnsupportedOperationException()
    }
}

internal fun ByteArray.toMsgPackArrayBuffer() = MsgPackDataInputArrayBuffer(this)
internal fun BufferedSource.toMsgPackBufferedSource() = MsgPackDataInputOkio(this)
//internal fun BufferedSource.toMsgPackBufferedSource() = MsgPackDataInputArrayBuffer(this.readByteArray())