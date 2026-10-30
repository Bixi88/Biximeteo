package app.meteopersonale.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionTitle(text: String, bars: Boolean) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(26.dp)) {
            val w = size.width
            val r = CornerRadius(1.5.dp.toPx())
            if (bars) {
                val hs = listOf(0.45f, 0.7f, 0.55f, 0.9f, 0.65f)
                val bw = w / 7f
                hs.forEachIndexed { i, hf ->
                    drawRoundRect(
                        Color.White,
                        Offset(i * bw * 1.4f, w * (1f - hf)),
                        Size(bw, w * hf),
                        r
                    )
                }
            } else {
                val s = w * 0.44f
                for (ix in 0..1) for (iy in 0..1) {
                    drawRoundRect(
                        Color.White,
                        Offset(ix * (s + w * 0.12f), iy * (s + w * 0.12f)),
                        Size(s, s),
                        r
                    )
                }
            }
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 26.sp,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}
