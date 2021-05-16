package webrtsp

import kotlin.math.min
import kotlin.math.max

class Token {
    val buffer: String
    var pos: Int = 0
        set(value) {
            field = min(max(value, 0), buffer.length)
            if(this.length > buffer.length - field.toInt())
                this.length = buffer.length - field.toInt()
        }
    var length: Int = 0
        set(value) {
            field = min(value, buffer.length - this.pos)
        }

    constructor(buffer: String, pos: Int) {
        this.buffer = buffer
        this.pos = pos
        this.length = 0
    }
    constructor(buffer: String, pos: Int, length: Int) {
        this.buffer = buffer
        this.pos = pos
        this.length = length
    }

    val empty: Boolean
        get() = pos >= buffer.length || length == 0
    val value: String
        get() = buffer.substring(pos, pos + length)

    fun startsWith(prefix: String): Boolean {
        return buffer.startsWith(prefix, pos)
    }
    fun equals(value: String): Boolean {
        return buffer.startsWith(value, pos) && length == value.length
    }
}

