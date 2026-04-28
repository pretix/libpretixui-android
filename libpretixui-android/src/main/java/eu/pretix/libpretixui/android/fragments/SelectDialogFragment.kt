package eu.pretix.libpretixui.android.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import kotlin.let

open class SelectDialogFragment : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val displayMetrics = requireContext().resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        if (dpWidth >= 840) { // Layout with receipt on the right side
            val window = dialog!!.window
            window?.setGravity(Gravity.CENTER or Gravity.CENTER)
            window?.attributes?.let { params ->
                params.x = params.x.minus((432 / 2 * displayMetrics.density).toInt())
                window.attributes = params
            }
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }
}