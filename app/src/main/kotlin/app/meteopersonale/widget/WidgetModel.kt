package app.meteopersonale.widget

import app.meteopersonale.data.DayPoint
import app.meteopersonale.data.HourPoint
import java.time.LocalDate

/** Una colonna del widget: giorno, icona, numero grande e numero piccolo. */
data class WidgetCol(val name: String, val symbol: String, val bigC: Int, val smallC: Int)

/**
 * Colonne del widget. Per oggi, come in Weawow, si mostrano la temperatura
 * e l'icona ATTUALI (di sera l'icona con la luna) e la minima del giorno.
 */
fun buildWidgetColumns(
    days: List<DayPoint>,
    current: HourPoint?,
    count: Int,
    today: LocalDate = LocalDate.now()
): List<WidgetCol> =
    days.filter { !it.date.isBefore(today) }.take(count).map { d ->
        if (d.date == today && current != null) {
            WidgetCol(
                widgetDayName(d.date), current.symbol,
                Math.round(current.tempC).toInt(), Math.round(d.minC).toInt()
            )
        } else {
            WidgetCol(
                widgetDayName(d.date), d.symbol,
                Math.round(d.maxC).toInt(), Math.round(d.minC).toInt()
            )
        }
    }
