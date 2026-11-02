package app.meteopersonale.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

enum class SceneKind {
    CLEAR_DAY, CLEAR_NIGHT, PARTLY_DAY, PARTLY_NIGHT, CLOUDY, FOG, RAIN, THUNDER, SNOW, SLEET
}

/** Dal symbol_code (stile MET Norway) al tipo di scena e al testo in italiano. */
object Conditions {
    fun kind(symbol: String): SceneKind {
        val night = symbol.endsWith("_night")
        return when {
            symbol.contains("thunder") -> SceneKind.THUNDER
            symbol.contains("sleet") -> SceneKind.SLEET
            symbol.contains("snow") -> SceneKind.SNOW
            symbol.contains("rain") -> SceneKind.RAIN
            symbol.startsWith("fog") -> SceneKind.FOG
            symbol.startsWith("partlycloudy") || symbol.startsWith("fair") ->
                if (night) SceneKind.PARTLY_NIGHT else SceneKind.PARTLY_DAY
            symbol.startsWith("clearsky") ->
                if (night) SceneKind.CLEAR_NIGHT else SceneKind.CLEAR_DAY
            else -> SceneKind.CLOUDY
        }
    }

    /** 0 = debole, 1 = normale, 2 = forte */
    fun intensity(symbol: String): Int = when {
        symbol.startsWith("light") -> 0
        symbol.startsWith("heavy") -> 2
        else -> 1
    }

    fun label(symbol: String): String {
        val i = intensity(symbol)
        val shower = symbol.contains("showers")
        return when (kind(symbol)) {
            SceneKind.THUNDER -> "Temporale"
            SceneKind.SLEET -> "Nevischio"
            SceneKind.SNOW -> if (shower) "Rovesci di neve" else when (i) {
                0 -> "Neve debole"
                2 -> "Neve intensa"
                else -> "Neve"
            }
            SceneKind.RAIN -> if (shower) when (i) {
                0 -> "Rovesci deboli"
                2 -> "Rovesci intensi"
                else -> "Rovesci"
            } else when (i) {
                0 -> "Pioggia debole"
                2 -> "Pioggia forte"
                else -> "Pioggia"
            }
            SceneKind.FOG -> "Nebbia"
            SceneKind.CLOUDY -> "Nuvoloso"
            SceneKind.PARTLY_DAY, SceneKind.PARTLY_NIGHT ->
                if (symbol.startsWith("fair")) "Poco nuvoloso" else "Parzialmente nuvoloso"
            SceneKind.CLEAR_DAY, SceneKind.CLEAR_NIGHT -> "Sereno"
        }
    }
}

private class Look(val top: Color, val bottom: Color, val hillBack: Color, val hillFront: Color)

private fun baseLook(k: SceneKind): Look = when (k) {
    SceneKind.CLEAR_DAY -> Look(Color(0xFF2E6BD0), Color(0xFF7DB8EE), Color(0xFF6C5FD0), Color(0xFF4A3FA8))
    SceneKind.CLEAR_NIGHT -> Look(Color(0xFF0B1030), Color(0xFF2A2F6B), Color(0xFF2B2466), Color(0xFF171447))
    SceneKind.PARTLY_DAY -> Look(Color(0xFF3A6FC4), Color(0xFF86B4E6), Color(0xFF6C5FD0), Color(0xFF4A3FA8))
    SceneKind.PARTLY_NIGHT -> Look(Color(0xFF10153A), Color(0xFF2F3573), Color(0xFF2B2466), Color(0xFF171447))
    SceneKind.CLOUDY -> Look(Color(0xFF4B5575), Color(0xFF8892B0), Color(0xFF4F4A86), Color(0xFF35316A))
    SceneKind.FOG -> Look(Color(0xFF6F748C), Color(0xFFB3B7C9), Color(0xFF7C7AA6), Color(0xFF625F8C))
    SceneKind.RAIN -> Look(Color(0xFF2D3190), Color(0xFF161B5A), Color(0xFF3A3FA5), Color(0xFF242A7C))
    SceneKind.THUNDER -> Look(Color(0xFF2A1F45), Color(0xFF0F0B1E), Color(0xFF2E2457), Color(0xFF1A1336))
    SceneKind.SNOW -> Look(Color(0xFF4A5A8C), Color(0xFF9FB0D8), Color(0xFFD6D9F2), Color(0xFFEDEEFA))
    SceneKind.SLEET -> Look(Color(0xFF3B4470), Color(0xFF7480A8), Color(0xFF8E8CC0), Color(0xFF6E6CA2))
}

/** Di notte le scene "da giorno" (neve, nebbia, nuvoloso...) si scuriscono verso il blu notte. */
private fun lookFor(k: SceneKind, dark: Boolean): Look {
    val l = baseLook(k)
    val nightKind = k == SceneKind.CLEAR_NIGHT || k == SceneKind.PARTLY_NIGHT
    if (!dark || nightKind) return l
    val a = if (k == SceneKind.RAIN || k == SceneKind.THUNDER) 0.35f else 0.7f
    val n = Color(0xFF070B24)
    return Look(lerp(l.top, n, a), lerp(l.bottom, n, a), lerp(l.hillBack, n, a - 0.1f), lerp(l.hillFront, n, a - 0.1f))
}

/** Se si sa se è notte (dall'ora e dalla posizione) ha la precedenza sul suffisso del codice meteo. */
private fun sceneKind(symbol: String, night: Boolean?): SceneKind {
    val k = Conditions.kind(symbol)
    if (night == null) return k
    return when {
        night && k == SceneKind.CLEAR_DAY -> SceneKind.CLEAR_NIGHT
        night && k == SceneKind.PARTLY_DAY -> SceneKind.PARTLY_NIGHT
        !night && k == SceneKind.CLEAR_NIGHT -> SceneKind.CLEAR_DAY
        !night && k == SceneKind.PARTLY_NIGHT -> SceneKind.PARTLY_DAY
        else -> k
    }
}

/** Nuvole in secondo piano: opacità e colore (null = nessuna). */
private fun cloudsFor(k: SceneKind): Pair<Float, Color>? = when (k) {
    SceneKind.CLOUDY -> 0.35f to Color.White
    SceneKind.PARTLY_DAY -> 0.30f to Color.White
    SceneKind.PARTLY_NIGHT -> 0.18f to Color(0xFFB8C0FF)
    SceneKind.RAIN -> 0.28f to Color(0xFFB7C0F2)
    SceneKind.THUNDER -> 0.55f to Color(0xFF0A0614)
    SceneKind.SNOW -> 0.30f to Color.White
    SceneKind.SLEET -> 0.28f to Color(0xFFCDD3F0)
    else -> null
}

private fun cloudsFor(k: SceneKind, dark: Boolean): Pair<Float, Color>? {
    val c = cloudsFor(k) ?: return null
    val soft = dark && (k == SceneKind.CLOUDY || k == SceneKind.SNOW || k == SceneKind.SLEET)
    return if (soft) (c.first * 0.55f) to c.second else c
}

private fun rainCount(k: SceneKind, i: Int): Int = when (k) {
    SceneKind.RAIN -> when (i) { 0 -> 45; 2 -> 130; else -> 80 }
    SceneKind.THUNDER -> 110
    SceneKind.SLEET -> 40
    else -> 0
}

private fun snowCount(k: SceneKind, i: Int): Int = when (k) {
    SceneKind.SNOW -> when (i) { 0 -> 30; 2 -> 110; else -> 60 }
    SceneKind.SLEET -> 35
    else -> 0
}

private class P(val x: Float, val y: Float, val off: Float, val k: Int, val size: Float, val a: Float)

private fun particles(n: Int, seed: Int, kMin: Int, kMax: Int): List<P> {
    val r = Random(seed)
    return List(n) {
        P(r.nextFloat(), r.nextFloat(), r.nextFloat(), kMin + r.nextInt(kMax - kMin + 1), r.nextFloat(), 0.25f + r.nextFloat() * 0.5f)
    }
}

private const val TWO_PI = (2.0 * PI).toFloat()

@Composable
fun WeatherScene(
    symbol: String,
    tempC: Double?,
    minC: Double?,
    maxC: Double?,
    title: String,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onGps: () -> Unit,
    isNightNow: Boolean? = null
) {
    val kind = remember(symbol, isNightNow) { sceneKind(symbol, isNightNow) }
    val intensity = remember(symbol) { Conditions.intensity(symbol) }
    val dark = isNightNow ?: (kind == SceneKind.CLEAR_NIGHT || kind == SceneKind.PARTLY_NIGHT)
    val look = remember(kind, dark) { lookFor(kind, dark) }
    val clouds = remember(kind, dark) { cloudsFor(kind, dark) }
    val rain = remember(kind, intensity) { particles(rainCount(kind, intensity), 7, 8, 12) }
    val snow = remember(kind, intensity) { particles(snowCount(kind, intensity), 11, 2, 4) }
    val night = kind == SceneKind.CLEAR_NIGHT || kind == SceneKind.PARTLY_NIGHT
    val stars = remember(night) { particles(if (night) 45 else 0, 3, 1, 6) }

    // un solo ciclo da 12 s: tutte le animazioni usano multipli interi, così il giro è senza scatti
    val infinite = rememberInfiniteTransition()
    val phase by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)))

    val shadow = Shadow(Color(0x4D000000), Offset(0f, 2f), 6f)

    Box(
        Modifier
            .statusBarsPadding()
            .padding(12.dp)
            .fillMaxWidth()
            .height(372.dp)
            .clip(RoundedCornerShape(28.dp))
    ) {
        // 1) cielo, sole/luna, stelle
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(look.top, look.bottom)))
            val glowCenter = Offset(24.dp.toPx() + 60.dp.toPx(), 84.dp.toPx() + 60.dp.toPx())
            when (kind) {
                SceneKind.CLEAR_DAY, SceneKind.PARTLY_DAY -> {
                    val r = 110.dp.toPx() * (1f + 0.05f * sin(phase * TWO_PI * 4f))
                    drawCircle(
                        brush = Brush.radialGradient(listOf(Color(0x99FFE79A), Color.Transparent), glowCenter, r),
                        radius = r,
                        center = glowCenter
                    )
                }
                SceneKind.CLEAR_NIGHT, SceneKind.PARTLY_NIGHT -> {
                    val r = 100.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(listOf(Color(0x55B8C4FF), Color.Transparent), glowCenter, r),
                        radius = r,
                        center = glowCenter
                    )
                    stars.forEach { p ->
                        val a = 0.25f + 0.65f * abs(sin(phase * TWO_PI * p.k + p.off * TWO_PI))
                        drawCircle(
                            color = Color.White.copy(alpha = a),
                            radius = (0.8f + p.size * 1.4f).dp.toPx(),
                            center = Offset(p.x * size.width, p.y * size.height * 0.55f)
                        )
                    }
                }
                else -> {}
            }
        }

        // 2) nuvole (livello a parte: l'opacità vale per tutta la nuvola, senza macchie nelle sovrapposizioni)
        if (clouds != null) {
            Canvas(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = clouds.first
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                val xs = floatArrayOf(0.02f, 0.42f, 0.72f)
                val ys = floatArrayOf(14f, 44f, 4f)
                val ss = floatArrayOf(1.7f, 2.2f, 1.5f)
                for (i in 0..2) {
                    val s = ss[i].dp.toPx()
                    val x = xs[i] * size.width + sin(phase * TWO_PI + i * 2.1f) * 0.05f * size.width
                    cloudShape(x, ys[i].dp.toPx(), s, clouds.second)
                }
            }
        }

        // 3) precipitazioni, nebbia, colline, lampi
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            when (kind) {
                SceneKind.RAIN -> drawRain(rain, phase, Color(0xFFB9C8FF))
                SceneKind.THUNDER -> drawRain(rain, phase, Color(0xFFC9C0FF))
                SceneKind.SLEET -> {
                    drawRain(rain, phase, Color(0xFFD0D8FF))
                    drawSnow(snow, phase)
                }
                SceneKind.SNOW -> drawSnow(snow, phase)
                SceneKind.FOG -> drawFog(phase)
                else -> {}
            }
            drawHills(look.hillBack, look.hillFront)

            if (kind == SceneKind.THUNDER) {
                val t2 = (phase * 2f) % 1f
                val fl = when {
                    t2 < 0.06f -> 0f
                    t2 < 0.08f -> 0.7f
                    t2 < 0.10f -> 0.1f
                    t2 < 0.13f -> 0.55f
                    else -> 0f
                }
                if (fl > 0f) {
                    drawRect(Color(0xFFCDD6FF).copy(alpha = fl * 0.55f))
                    val bolt = Path().apply {
                        moveTo(w * 0.72f, 0f)
                        lineTo(w * 0.62f, h * 0.35f)
                        lineTo(w * 0.69f, h * 0.35f)
                        lineTo(w * 0.63f, h * 0.64f)
                        lineTo(w * 0.82f, h * 0.28f)
                        lineTo(w * 0.74f, h * 0.28f)
                        lineTo(w * 0.81f, 0f)
                        close()
                    }
                    drawPath(bolt, Color(0xFFFFF3B8).copy(alpha = fl.coerceAtLeast(0.4f)))
                }
            }
        }

        // barra in alto: tre pulsanti piatti, nome della città al centro
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SceneButton("Menu", onMenu) { c ->
                for (k in -1..1) {
                    val y = size.height / 2f + k * 5.dp.toPx()
                    drawLine(c, Offset(size.width * 0.2f, y), Offset(size.width * 0.8f, y), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color.White, fontSize = 24.sp, shadow = shadow)
            )
            SceneButton("Cerca città", onSearch) { c ->
                val ctr = Offset(size.width * 0.46f, size.height * 0.46f)
                val r = size.width * 0.2f
                drawCircle(color = c, radius = r, center = ctr, style = Stroke(2.dp.toPx()))
                drawLine(
                    c,
                    Offset(ctr.x + r * 0.72f, ctr.y + r * 0.72f),
                    Offset(size.width * 0.8f, size.height * 0.8f),
                    2.dp.toPx(), StrokeCap.Round
                )
            }
            SceneButton("Usa la posizione", onGps) { c ->
                val ctr = Offset(size.width / 2f, size.height / 2f)
                drawCircle(color = c, radius = size.width * 0.1f, center = ctr)
                drawCircle(color = c, radius = size.width * 0.22f, center = ctr, style = Stroke(2.dp.toPx()))
                val a = size.width * 0.3f
                val b = size.width * 0.42f
                drawLine(c, Offset(ctr.x, ctr.y - a), Offset(ctr.x, ctr.y - b), 2.dp.toPx(), StrokeCap.Round)
                drawLine(c, Offset(ctr.x, ctr.y + a), Offset(ctr.x, ctr.y + b), 2.dp.toPx(), StrokeCap.Round)
                drawLine(c, Offset(ctr.x - a, ctr.y), Offset(ctr.x - b, ctr.y), 2.dp.toPx(), StrokeCap.Round)
                drawLine(c, Offset(ctr.x + a, ctr.y), Offset(ctr.x + b, ctr.y), 2.dp.toPx(), StrokeCap.Round)
            }
        }

        // icona originale + temperatura
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 84.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                painter = painterResource(WeatherIcons.resFor(symbol)),
                contentDescription = null,
                modifier = Modifier.size(120.dp)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = tempC?.let { "${Math.round(it)}°" } ?: "–",
                    style = TextStyle(fontFamily = SfThin, fontSize = 88.sp, color = Color.White, shadow = shadow)
                )
                if (minC != null && maxC != null) {
                    Text(
                        text = "Min ${Math.round(minC)}° · Max ${Math.round(maxC)}°",
                        style = TextStyle(fontSize = 15.sp, color = Color(0xD9FFFFFF), shadow = shadow)
                    )
                }
            }
        }

        Text(
            text = Conditions.label(symbol),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 238.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(color = Color.White, fontSize = 19.sp, shadow = shadow)
        )
    }
}

@Composable
private fun SceneButton(label: String, onClick: () -> Unit, draw: DrawScope.(Color) -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) {
            translate(top = 1.2.dp.toPx()) { draw(Color(0x66000000)) }
            draw(Color.White)
        }
    }
}

private fun DrawScope.cloudShape(x: Float, y: Float, s: Float, c: Color) {
    drawCircle(color = c, radius = 22f * s, center = Offset(x + 40f * s, y + 46f * s))
    drawCircle(color = c, radius = 28f * s, center = Offset(x + 68f * s, y + 36f * s))
    drawCircle(color = c, radius = 20f * s, center = Offset(x + 96f * s, y + 48f * s))
    drawRoundRect(c, Offset(x + 40f * s, y + 46f * s), Size(56f * s, 22f * s), CornerRadius(11f * s))
}

private fun DrawScope.drawRain(ps: List<P>, phase: Float, color: Color) {
    val w = size.width
    val h = size.height
    val pad = 40.dp.toPx()
    ps.forEach { p ->
        val prog = (phase * p.k + p.off) % 1f
        val y = -pad + prog * (h + 2f * pad)
        val len = (10f + p.size * 16f).dp.toPx()
        val x0 = p.x * w - y * 0.18f
        val x = ((x0 % w) + w) % w
        drawLine(
            color.copy(alpha = p.a),
            Offset(x, y),
            Offset(x + len * 0.18f, y - len),
            strokeWidth = (1f + p.size * 0.8f).dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSnow(ps: List<P>, phase: Float) {
    val w = size.width
    val h = size.height
    ps.forEach { p ->
        val prog = (phase * p.k + p.off) % 1f
        val y = -10.dp.toPx() + prog * (h + 20.dp.toPx())
        val sway = sin(phase * TWO_PI * p.k * 2f + p.off * TWO_PI) * 14.dp.toPx()
        drawCircle(
            color = Color.White.copy(alpha = 0.55f + 0.4f * p.a),
            radius = (1.2f + p.size * 2.4f).dp.toPx(),
            center = Offset(p.x * w + sway, y)
        )
    }
}

private fun DrawScope.drawFog(phase: Float) {
    val w = size.width
    val h = size.height
    val bandH = 78.dp.toPx()
    val bw = w * 1.2f
    for (i in 0..3) {
        val x = (i * 0.18f - 0.15f) * w + sin(phase * TWO_PI + i * 1.7f) * 0.2f * w
        val y = h * (0.10f + 0.2f * i)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color(0x55FFFFFF), Color.Transparent),
                startX = x, endX = x + bw
            ),
            topLeft = Offset(x, y),
            size = Size(bw, bandH),
            cornerRadius = CornerRadius(bandH / 2f)
        )
    }
}

private fun DrawScope.drawHills(back: Color, front: Color) {
    val w = size.width
    val hh = 110.dp.toPx()
    val top = size.height - hh
    fun sx(x: Float) = x / 366f * w
    fun sy(y: Float) = top + y / 110f * hh
    val b = Path().apply {
        moveTo(sx(0f), sy(40f))
        cubicTo(sx(60f), sy(10f), sx(120f), sy(20f), sx(190f), sy(44f))
        cubicTo(sx(260f), sy(68f), sx(320f), sy(40f), sx(366f), sy(18f))
        lineTo(sx(366f), sy(110f))
        lineTo(sx(0f), sy(110f))
        close()
    }
    drawPath(b, back)
    val f = Path().apply {
        moveTo(sx(0f), sy(72f))
        cubicTo(sx(70f), sy(50f), sx(130f), sy(60f), sx(200f), sy(76f))
        cubicTo(sx(270f), sy(92f), sx(320f), sy(70f), sx(366f), sy(56f))
        lineTo(sx(366f), sy(110f))
        lineTo(sx(0f), sy(110f))
        close()
    }
    drawPath(f, front)
}
