package app.meteopersonale.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meteopersonale.data.HourPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun HourlySection(hours: List<HourPoint>, zone: ZoneId) {
    if (hours.isEmpty()) return
    val colW = 61.dp
    val curveH = 84.dp
    val measurer = rememberTextMeasurer()
    val timeFmt = remember(zone) { DateTimeFormatter.ofPattern("H:mm").withZone(zone) }
    val tempStyle = TextStyle(fontFamily = SfThin, fontSize = 19.sp, color = TileText)
    val nowHour = remember { Instant.now().truncatedTo(ChronoUnit.HOURS) }

    val minT = hours.minOf { it.tempC }
    val maxT = hours.maxOf { it.tempC }

    Tile("Ore", bars = true) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                // ora + icona originale
                Row {
                    hours.forEach { p ->
                        val now = p.time == nowHour
                        Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (now) "Ora" else timeFmt.format(p.time),
                                color = if (now) TileAccent else TileDim,
                                fontSize = 13.sp,
                                modifier = Modifier.height(20.dp)
                            )
                            Image(
                                painter = painterResource(WeatherIcons.resFor(p.symbol)),
                                contentDescription = null,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }
                }

                // curva della temperatura, in una fascia tutta sua (non tocca le icone)
                Canvas(
                    Modifier
                        .width(colW * hours.size)
                        .height(curveH)
                ) {
                    val colPx = colW.toPx()
                    val h = size.height
                    val topPad = 30.dp.toPx()
                    val botPad = 14.dp.toPx()
                    val usable = h - topPad - botPad
                    val span = maxOf(maxT - minT, 4.0)
                    val mid = (maxT + minT) / 2.0
                    fun yOf(t: Double): Float = topPad + usable / 2f - ((t - mid) / span).toFloat() * usable

                    val pts = hours.mapIndexed { i, p -> Offset((i + 0.5f) * colPx, yOf(p.tempC)) }
                    val line = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        for (i in 1 until pts.size) {
                            val a = pts[i - 1]
                            val b = pts[i]
                            val mx = (a.x + b.x) / 2f
                            cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                        }
                    }
                    drawPath(
                        line, TileGold,
                        style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    pts.forEach {
                        drawCircle(color = TileBg, radius = 3.5.dp.toPx(), center = it)
                        drawCircle(color = TileGold, radius = 3.5.dp.toPx(), center = it, style = Stroke(2.dp.toPx()))
                    }
                    hours.forEachIndexed { i, p ->
                        val txt = "${Math.round(p.tempC)}°"
                        val layout = measurer.measure(txt, tempStyle)
                        drawText(
                            measurer, txt,
                            Offset(
                                pts[i].x - layout.size.width / 2f,
                                pts[i].y - layout.size.height - 7.dp.toPx()
                            ),
                            tempStyle
                        )
                    }
                }

                // pioggia: barra + mm
                Row {
                    hours.forEach { p ->
                        Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.height(34.dp), contentAlignment = Alignment.BottomCenter) {
                                if (p.precipMm >= 0.05) {
                                    Box(
                                        Modifier
                                            .width(8.dp)
                                            .height((p.precipMm.toFloat() * 16f).coerceIn(3f, 34f).dp)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(TileRain)
                                    )
                                }
                            }
                            Text(
                                if (p.precipMm >= 0.05) buildAnnotatedString {
                                    append(String.format(Locale.US, "%.1f", p.precipMm))
                                    withStyle(SpanStyle(fontSize = 10.sp)) { append("mm") }
                                } else AnnotatedString(""),
                                color = TileRain,
                                fontSize = 13.sp,
                                maxLines = 1,
                                modifier = Modifier.height(20.dp)
                            )
                        }
                    }
                }

                // vento
                Row {
                    hours.forEach { p ->
                        Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                buildAnnotatedString {
                                    append(windKmh(p.windMs).toString())
                                    withStyle(SpanStyle(fontSize = 9.sp)) { append("km/h") }
                                },
                                color = WindColor,
                                fontSize = 13.sp,
                                maxLines = 1,
                                modifier = Modifier.height(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
