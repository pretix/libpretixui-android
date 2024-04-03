package eu.pretix.libpretixui.android.questions

import android.app.DatePickerDialog
import android.content.Context
import android.os.Build
import android.text.InputType
import androidx.appcompat.widget.AppCompatEditText
import java.text.DateFormat
import java.util.*


class DatePickerField(context: Context, minDate: Long? = null, maxDate: Long? = null) : AppCompatEditText(context) {
    internal var cal = Calendar.getInstance()
    internal var dateFormat: DateFormat = android.text.format.DateFormat.getDateFormat(context)
    internal var set = false

    internal var dateChangeListener: DatePickerDialog.OnDateSetListener = DatePickerDialog.OnDateSetListener { view, year, monthOfYear, dayOfMonth ->
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, monthOfYear)
        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

        set = true
        setText(dateFormat.format(cal.time))
    }

    var value: Calendar?
        get() = if (!set) {
            null
        } else cal.clone() as Calendar
        set(cal) {
            this.cal = cal?.clone() as Calendar
            set = true
            setText(dateFormat.format(cal.time))
        }


    init {
        isFocusableInTouchMode = false
        isFocusable = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            showSoftInputOnFocus = false
        }
        setRawInputType(InputType.TYPE_NULL)
        setTextIsSelectable(true)
        keyListener = null

        setOnFocusChangeListener { view, b ->
            if (b && view.isInTouchMode) {
                view.callOnClick()
            }
        }
        setOnClickListener {
            val dialog = DatePickerDialog(
                    context,
                    dateChangeListener,
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
            )
            if (maxDate != null) {
                dialog.datePicker.maxDate = maxDate
            }
            if (minDate != null) {
                dialog.datePicker.minDate = minDate
            }
            dialog.show()
        }
    }

    fun setValue(date: Date) {
        cal.time = date
        set = true
        setText(dateFormat.format(cal.time))
    }
}
