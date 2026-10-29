package com.juzhen.ambientlight.widget

import android.content.Context

/**
 * The lamp this product is shipped with. Configured through `ambient.pinnedMac`
 * in gradle.properties and exposed as `BuildConfig.PINNED_MAC`, so it can be
 * changed without touching code.
 *
 * Any desktop-card tap auto-connects to this address.
 */
object PinnedLamp {
    const val DEFAULT_MAC: String = "FF:FF:10:98:3C:D9"
}
