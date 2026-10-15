// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.platform

import android.content.Context
import io.github.coreharbor.brewdoro.domain.UserPreferences
import io.github.coreharbor.brewdoro.domain.UserPreferencesCodec
import java.io.IOException

class UserPreferencesStore(context: Context) {
    private val store = context.getSharedPreferences("comfort", Context.MODE_PRIVATE)
    fun load(): UserPreferences = UserPreferencesCodec.decode(
        try { store.getString("preferences", null) } catch (_: ClassCastException) { null })
    fun save(value: UserPreferences) {
        if (!store.edit().putString("preferences", UserPreferencesCodec.encode(value)).commit())
            throw IOException("Comfort preference commit failed")
    }
}
