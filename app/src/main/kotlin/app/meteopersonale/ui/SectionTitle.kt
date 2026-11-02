package app.meteopersonale.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette condivisa dei tile (nomi con prefisso per non confliggere con le altre schermate)
internal val TileBg = Color(0xFF1C1C1E)
internal val TileText = Color(0xFFEDEDED)
internal val TileDim = Color(0xFF9A9A9A)
internal val TileAccent = Color(0xFFD0BCFF)
internal val TileGold = Color(0xFFE0A93A)
internal val TileRain = Color(0xFF3B8DD0)

/** Card grigia su sfondo nero, con titolo e icona lilla. */
@Composable
fun Tile(title: String, bars: Boolean, content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TileBg)
            .padding(bottom = 10.dp)
    ) {
        TileTitle(title, bars)
        content()
    }
}

@Composable
private fun TileTitle(text: String, bars: Boolean) {
    Row(
        modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val r = CornerRadius(1.5.dp.toPx())
            if (bars) {
                val hs = listOf(0.45f, 0.7f, 0.55f, 0.9f, 0.65f)
                val bw = w / 7f
                hs.forEachIndexed { i, hf ->
                    drawRoundRect(
                        TileAccent,
                        Offset(i * bw * 1.4f, w * (1f - hf)),
                        Size(bw, w * hf),
                        r
                    )
                }
            } else {
                val s = w * 0.44f
                for (ix in 0..1) for (iy in 0..1) {
                    drawRoundRect(
                        TileAccent,
                        Offset(ix * (s + w * 0.12f), iy * (s + w * 0.12f)),
                        Size(s, s),
                        r
                    )
                }
            }
        }
        Text(
            text = text,
            color = TileText,
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
