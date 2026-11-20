package tech.qigai.helium.data.device

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.qigai.helium.agent.tools.CalEvent
import tech.qigai.helium.agent.tools.CalendarSource

/** Read-only view of the device calendars through CalendarContract.Instances (recurring events expanded). */
class AndroidCalendar(private val context: Context) : CalendarSource {
    override suspend fun events(begin: Long, end: Long): List<CalEvent> = withContext(Dispatchers.IO) {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.STATUS,
        )
        val out = ArrayList<CalEvent>()
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < 200) {
                if (!c.isNull(6) && c.getInt(6) == CalendarContract.Instances.STATUS_CANCELED) continue
                out += CalEvent(
                    title = c.getString(0).orEmpty(),
                    begin = c.getLong(1),
                    end = c.getLong(2),
                    allDay = c.getInt(3) == 1,
                    location = c.getString(4).orEmpty(),
                    calendar = c.getString(5).orEmpty(),
                )
            }
        }
        out
    }
}
