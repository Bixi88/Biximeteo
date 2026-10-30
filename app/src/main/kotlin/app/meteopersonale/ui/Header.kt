package app.meteopersonale.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Sky = Brush.verticalGradient(listOf(Color(0xFF3B5E94), Color(0xFF1C3257)))

@Composable
fun Header(
    title: String,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onGps: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Sky)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundButton(Color(0x99252A33), onMenu) {
            val w = size.width
            val h = size.height
            for (k in -1..1) {
                val y = h / 2f + k * 5.dp.toPx()
                drawLine(Color.White, Offset(w * 0.28f, y), Offset(w * 0.72f, y), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 26.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        )
        RoundButton(Color(0x99252A33), onSearch) {
            val c = Offset(size.width * 0.46f, size.height * 0.46f)
            val r = size.width * 0.17f
            drawCircle(Color.White, r, c, style = Stroke(2.2.dp.toPx()))
            drawLine(
                Color.White,
                Offset(c.x + r * 0.72f, c.y + r * 0.72f),
                Offset(size.width * 0.72f, size.height * 0.72f),
                2.6.dp.toPx(), StrokeCap.Round
            )
        }
        Box(Modifier.size(10.dp))
        RoundButton(Color(0xAA16243A), onGps) {
            val blue = Color(0xFF3B9BF0)
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(blue, size.width * 0.09f, c)
            drawCircle(blue, size.width * 0.20f, c, style = Stroke(2.2.dp.toPx()))
            val a = size.width * 0.27f
            val b = size.width * 0.36f
            drawLine(blue, Offset(c.x, c.y - a), Offset(c.x, c.y - b), 2.2.dp.toPx(), StrokeCap.Round)
            drawLine(blue, Offset(c.x, c.y + a), Offset(c.x, c.y + b), 2.2.dp.toPx(), StrokeCap.Round)
            drawLine(blue, Offset(c.x - a, c.y), Offset(c.x - b, c.y), 2.2.dp.toPx(), StrokeCap.Round)
            drawLine(blue, Offset(c.x + a, c.y), Offset(c.x + b, c.y), 2.2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun RoundButton(
    bg: Color,
    onClick: () -> Unit,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.size(42.dp)) { draw() }
    }
}
