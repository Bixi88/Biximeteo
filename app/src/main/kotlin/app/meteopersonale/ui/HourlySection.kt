package app.meteopersonale.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meteopersonale.data.HourPoint
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private val LineColor = Color(0xFFB8862B)
private val StripeColor = Color(0xFF161616)
private val RainBlue = Color(0xFF3B8DD0)

@Composable
fun HourlySection(hours: List<HourPoint>, zone: ZoneId) {
    if (hours.isEmpty()) return
    val colW = 54.dp
    val chartH = 132.dp
    val measurer = rememberTextMeasurer()
    val timeFmt = remember(zone) { DateTimeFormatter.ofPattern("H:mm").withZone(zone) }
    val tempStyle = TextStyle(fontFamily = SfThin, fontSize = 19.sp, color = Color.White)

    val minT = hours.minOf { it.tempC }
    val maxT = hours.maxOf { it.tempC }

    SectionTitle("Ore", bars = true)

    Row(Modifier.horizontalScroll(rememberScrollState())) {
        Column {
            Canvas(
                Modifier
                    .width(colW * hours.size)
                    .height(chartH)
            ) {
                val colPx = colW.toPx()
                val h = size.height

                // strisce alternate (ore pari più scure)
                hours.forEachIndexed { i, p ->
                    if (p.time.atZone(zone).hour % 2 == 0) {
                        drawRect(StripeColor, Offset(i * colPx, 0f), Size(colPx, h))
                    }
                }

                // scala fissa ~9,5 dp per grado, ridotta se l'escursione è grande
                val range = (maxT - minT).coerceAtLeast(0.0)
                val perDeg = min(9.5.dp.toPx(), (0.42f * h) / range.coerceAtLeast(1.0).toFloat())
                val mid = ((minT + maxT) / 2.0).toFloat()
                val midY = 0.60f * h
                fun yOf(t: Double): Float = midY - ((t.toFloat() - mid) * perDeg)

                val pts = hours.mapIndexed { i, p -> Offset((i + 0.5f) * colPx, yOf(p.tempC)) }

                val line = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                val fill = Path().apply {
                    moveTo(pts.first().x, h)
                    pts.forEach { lineTo(it.x, it.y) }
                    lineTo(pts.last().x, h)
                    close()
                }
                val topY = pts.minOf { it.y }
                drawPath(
                    fill,
                    Brush.verticalGradient(
                        listOf(Color(0x66B8862B), Color(0x00B8862B)),
                        startY = topY, endY = h
                    )
                )
                drawPath(
                    line, LineColor,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawLine(Color(0xFF333333), Offset(0f, h - 1f), Offset(size.width, h - 1f), 1.dp.toPx())

                hours.forEachIndexed { i, p ->
                    val txt = "${Math.round(p.tempC)}°"
                    val layout = measurer.measure(txt, tempStyle)
                    drawText(
                        measurer, txt,
                        Offset(
                            pts[i].x - layout.size.width / 2f,
                            pts[i].y - layout.size.height - 6.dp.toPx()
                        ),
                        tempStyle
                    )
                }
            }

            Row {
                hours.forEach { p ->
                    Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(WeatherIcons.resFor(p.symbol)),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            timeFmt.format(p.time),
                            color = Color(0xFFEDEDED),
                            fontSize = 15.sp,
                            modifier = Modifier.height(24.dp)
                        )
                        Text(
                            if (p.precipMm >= 0.05) String.format(Locale.US, "%.1f", p.precipMm) else "",
                            color = RainBlue,
                            fontSize = 13.sp,
                            modifier = Modifier.height(20.dp)
                        )
                    }
                }
            }
        }
    }
}
