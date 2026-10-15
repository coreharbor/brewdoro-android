// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** One versioned preference value: partial field updates cannot create mixed sessions. */
object SnapshotCodec {
    fun encode(s: TimerSnapshot): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(2)
            out.writeInt(s.settings.focusMinutes)
            out.writeInt(s.settings.shortBreakMinutes)
            out.writeInt(s.settings.longBreakMinutes)
            out.writeBoolean(s.settings.cycleEnabled)
            out.writeBoolean(s.settings.autoStartEnabled)
            out.writeUTF(s.mode.name)
            out.writeUTF(s.status.name)
            out.writeLong(s.durationMs)
            out.writeLong(s.remainingMs)
            out.writeInt(s.completedFocus)
            out.writeUTF(s.nextMode?.name.orEmpty())
            out.writeUTF(s.generation)
            out.writeLong(s.deadlineElapsedMs ?: -1)
            out.writeLong(s.deadlineWallMs ?: -1)
            out.writeUTF(s.bootId.orEmpty())
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun DataInputStream.readStrictBoolean(): Boolean {
        val value = readUnsignedByte()
        require(value == 0 || value == 1)
        return value == 1
    }

    fun decode(encoded: String?): TimerSnapshot? {
        if (encoded == null || encoded.length > 4096) return null
        return try {
            DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
                val version = input.readInt()
                require(version in 1..2)
                val focus = input.readInt()
                val short = input.readInt()
                val long = input.readInt()
                // V1 retains every session field and opts into the requested Android
                // defaults. Do not synthesize a completion or discard an active timer.
                val settings = TimerSettings(focus, short, long,
                    if (version == 2) input.readStrictBoolean() else true,
                    if (version == 2) input.readStrictBoolean() else true)
                val mode = TimerMode.valueOf(input.readUTF())
                val status = TimerStatus.valueOf(input.readUTF())
                val duration = input.readLong()
                val remaining = input.readLong()
                val count = input.readInt()
                val next = input.readUTF().takeIf { it.isNotEmpty() }?.let { TimerMode.valueOf(it) }
                val generation = input.readUTF()
                val elapsed = input.readLong().also { require(it >= -1) }.takeUnless { it == -1L }
                val wall = input.readLong().also { require(it >= -1) }.takeUnless { it == -1L }
                val boot = input.readUTF().takeIf { it.isNotEmpty() }
                require(input.available() == 0)
                TimerSnapshot(settings, mode, status, duration, remaining, count, next, generation, elapsed, wall, boot)
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
    }
}
