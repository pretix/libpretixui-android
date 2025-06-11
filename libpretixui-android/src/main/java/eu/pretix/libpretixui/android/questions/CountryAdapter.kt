package eu.pretix.libpretixui.android.questions

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.neovisionaries.i18n.CountryCode
import eu.pretix.libpretixui.android.R

fun allCountries(): List<CountryCode> {
    val countries = mutableListOf<CountryCode>()
    countries.addAll(CountryCode.values().filter { it.assignment == CountryCode.Assignment.OFFICIALLY_ASSIGNED || it.alpha2 == "XK" })
    countries.sortBy { it.getName() }
    return countries
}

internal class CountryAdapter(context: Context) :
    ArrayAdapter<CountryCode>(context, R.layout.spinneritem_simple, allCountries()) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.spinneritem_simple, parent, false)
        (v as TextView).text = getItem(position)!!.getName()
        return v
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val v = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.spinneritem_simple, parent, false)
        (v as TextView).text = getItem(position)!!.getName()
        return v
    }

    fun getIndex(cc: CountryCode): Int {
        for (i in 0 until count) {
            if (getItem(i) == cc) {
                return i
            }
        }
        return -1
    }
}
