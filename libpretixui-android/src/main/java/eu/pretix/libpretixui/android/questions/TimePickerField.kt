package eu.pretix.libpretixui.android.questions

import android.app.TimePickerDialog
import android.content.Context
import androidx.appcompat.widget.AppCompatEditText
import org.joda.time.LocalTime


class TimePickerField(context: Context) : AppCompatEditText(context) {
    internal var localTime = LocalTime()
    internal var dateFormat: java.text.DateFormat = android.text.format.DateFormat.getTimeFormat(context)
    internal var set = false

    internal var timeChangeListener: TimePickerDialog.OnTimeSetListener = TimePickerDialog.OnTimeSetListener { timePicker, i, i1 ->
        localTime = localTime.withHourOfDay(i).withMinuteOfHour(i1)
        setText(dateFormat.format(localTime.toDateTimeToday().toDate()))
        set = true
    }

    var value: LocalTime?
        get() = if (!set) {
            null
        } else localTime
        set(t) {
            localTime = t!!
            set = true
            setText(dateFormat.format(t.toDateTimeToday().toDate()))
        }

    init {
        isFocusable = false

        setOnClickListener {
            TimePickerDialog(
                    context,
                    timeChangeListener,
                    localTime.hourOfDay,
                    localTime.minuteOfHour,
                    android.text.format.DateFormat.is24HourFormat(context)
            ).show()
        }
    }
}
