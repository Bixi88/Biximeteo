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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meteopersonale.data.DayPoint
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private val StripeColor = Color(0xFF161616)
private val RainBlue = Color(0xFF3B8DD0)

@Composable
fun DailySection(days: List<DayPoint>) {
    if (days.isEmpty()) return
    val colW = 58.dp
    val chartH = 230.dp
    val measurer = rememberTextMeasurer()
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEE d", Locale.ITALY) }
    val tempStyle = TextStyle(fontFamily = SfThin, fontSize = 19.sp, color = Color.White)

    val allMin = days.minOf { it.minC }
    val allMax = days.maxOf { it.maxC }
    val maxP = days.maxOf { it.precipMm }

    SectionTitle("Giorni", bars = false)

    Row(Modifier.horizontalScroll(rememberScrollState())) {
        days.forEachIndexed { i, d ->
            Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(
                    Modifier
                        .width(colW)
                        .height(chartH)
                ) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    if (i % 2 == 0) drawRect(StripeColor, Offset(0f, 0f), Size(w, h))

                    // precipitazioni: barra azzurra dal basso (~13,5 dp per mm)
                    if (d.precipMm > 0.0 && maxP > 0.0) {
                        val perMm = min(13.5.dp.toPx(), 0.85f * h / maxP.toFloat())
                        val bh = (d.precipMm.toFloat() * perMm).coerceAtLeast(3.dp.toPx())
                        val bw = w * 0.66f
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2E73A8), Color(0xFF1B3F66)),
                                startY = h - bh, endY = h
                            ),
                            Offset(cx - bw / 2f, h - bh),
                            Size(bw, bh)
                        )
                    }

                    // barra delle temperature
                    val top = 0.20f * h
                    val bottom = 0.78f * h
                    val span = (allMax - allMin).coerceAtLeast(1.0).toFloat()
                    fun yOf(t: Double): Float = bottom - ((t - allMin).toFloat() / span) * (bottom - top)
                    val yMax = yOf(d.maxC)
                    val yMin = maxOf(yOf(d.minC), yMax + 6.dp.toPx())
                    drawRoundRect(
                        Brush.verticalGradient(
                            listOf(Color(0xFFB5810C), Color(0xFFA6985A)),
                            startY = yMax, endY = yMin
                        ),
                        Offset(cx - 3.dp.toPx(), yMax),
                        Size(6.dp.toPx(), yMin - yMax),
                        CornerRadius(3.dp.toPx())
                    )

                    val tMax = "${Math.round(d.maxC)}°"
                    val tMin = "${Math.round(d.minC)}°"
                    val lMax = measurer.measure(tMax, tempStyle)
                    val lMin = measurer.measure(tMin, tempStyle)
                    drawText(
                        measurer, tMax,
                        Offset(cx - lMax.size.width / 2f, yMax - lMax.size.height - 4.dp.toPx()),
                        tempStyle
                    )
                    drawText(
                        measurer, tMin,
                        Offset(cx - lMin.size.width / 2f, yMin + 4.dp.toPx()),
                        tempStyle
                    )

                    drawLine(Color(0xFF333333), Offset(0f, h - 1f), Offset(w, h - 1f), 1.dp.toPx())
                }

                val wet = d.precipMm >= 0.05
                Text(
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontFamily = SfThin,
                                fontSize = 19.sp,
                                color = if (wet) RainBlue else Color(0xFFEDEDED)
                            )
                        ) {
                            append(
                                if (d.precipMm >= 10) Math.round(d.precipMm).toString()
                                else String.format(Locale.US, "%.1f", d.precipMm).removeSuffix(".0")
                            )
                        }
                        withStyle(
                            SpanStyle(fontSize = 13.sp, color = if (wet) RainBlue else Color(0xFFEDEDED))
                        ) {
                            append("mm")
                        }
                    },
                    modifier = Modifier.height(28.dp)
                )
                Image(
                    painter = painterResource(WeatherIcons.resFor(d.symbol)),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    dayFmt.format(d.date).replaceFirstChar { it.uppercase() },
                    color = Color(0xFFEDEDED),
                    fontSize = 15.sp,
                    modifier = Modifier.height(30.dp)
                )
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontFamily = SfThin, fontSize = 17.sp, color = WindColor)) {
                            append(windKmh(d.windMaxMs).toString())
                        }
                        withStyle(SpanStyle(fontSize = 11.sp, color = WindColor)) { append("km/h") }
                    },
                    maxLines = 1,
                    modifier = Modifier.height(26.dp)
                )
            }
        }
    }
}
