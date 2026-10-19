package com.aetheraquest.android

import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/** Decodes SmartFox packets compressed as `Z` + base64(zlib bytes). */
internal fun decodeSmartFoxPacket(packet: String): String {
    if (!packet.startsWith('Z') || packet.length == 1) return packet
    val compressed = try {
        Base64.getDecoder().decode(packet.substring(1))
    } catch (_: IllegalArgumentException) {
        return packet
    }
    return inflate(compressed, nowrap = false)
        ?: inflate(compressed, nowrap = true)
        ?: packet
}

private fun inflate(bytes: ByteArray, nowrap: Boolean): String? {
    val inflater = Inflater(nowrap)
    return try {
        inflater.setInput(bytes)
        val output = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(chunk)
            if (count > 0) output.write(chunk, 0, count)
            else if (inflater.needsInput() || inflater.needsDictionary()) return null
        }
        output.toString(Charsets.UTF_8.name())
    } catch (_: DataFormatException) {
        null
    } finally {
        inflater.end()
    }
}
