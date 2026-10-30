package app.meteopersonale.ui

import androidx.compose.ui.graphics.Color

/** Vento da m/s a km/h, arrotondato. */
internal fun windKmh(ms: Double): Int = Math.round(ms * 3.6).toInt()

internal val WindColor = Color(0xFFC5BBD6)
