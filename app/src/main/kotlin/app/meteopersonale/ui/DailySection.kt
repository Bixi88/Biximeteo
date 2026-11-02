package app.meteopersonale.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meteopersonale.data.DayPoint
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DailySection(days: List<DayPoint>) {
    if (days.isEmpty()) return
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEE d", Locale.ITALY) }
    val allMin = days.minOf { it.minC }
    val allMax = days.maxOf { it.maxC }
    val span = (allMax - allMin).coerceAtLeast(1.0).toFloat()

    Tile("Giorni", bars = false) {
        days.forEach { d ->
            val wet = d.precipMm >= 0.05
            val mm = if (d.precipMm >= 10) Math.round(d.precipMm).toString()
            else String.format(Locale.US, "%.1f", d.precipMm).removeSuffix(".0")
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    dayFmt.format(d.date).replaceFirstChar { it.uppercase() },
                    modifier = Modifier.width(58.dp),
                    color = TileText,
                    fontSize = 15.sp
                )
                Image(
                    painter = painterResource(WeatherIcons.resFor(d.symbol)),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
                Column(Modifier.width(54.dp), horizontalAlignment = Alignment.End) {
                    Text(
                        "$mm mm",
                        color = if (wet) TileRain else Color(0xFF6F6F6F),
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    Text("${windKmh(d.windMaxMs)} km/h", color = WindColor, fontSize = 11.sp, maxLines = 1)
                }
                Text(
                    "${Math.round(d.minC)}°",
                    modifier = Modifier.width(30.dp),
                    textAlign = TextAlign.End,
                    color = TileDim,
                    fontSize = 14.sp
                )
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                ) {
                    val w = size.width
                    val r = CornerRadius(3.dp.toPx())
                    drawRoundRect(Color(0xFF2C2C2E), Offset.Zero, size, r)
                    val left = ((d.minC.toFloat() - allMin.toFloat()) / span) * w
                    val bw = (((d.maxC - d.minC).toFloat() / span) * w).coerceAtLeast(8.dp.toPx())
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Color(0xFFC9A24A), TileGold), startX = left, endX = left + bw),
                        Offset(left.coerceAtMost(w - bw), 0f),
                        Size(bw, size.height),
                        r
                    )
                }
                Text(
                    "${Math.round(d.maxC)}°",
                    modifier = Modifier.width(30.dp),
                    color = TileText,
                    fontSize = 14.sp
                )
            }
        }
    }
}
