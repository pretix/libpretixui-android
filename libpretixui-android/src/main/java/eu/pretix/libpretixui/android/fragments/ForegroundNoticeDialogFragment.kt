package eu.pretix.libpretixui.android.fragments

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.edit
import eu.pretix.libpretixui.android.BuildConfig
import eu.pretix.libpretixui.android.R
import eu.pretix.libpretixui.android.databinding.DialogForegroundNoticeBinding
import kotlin.text.contains

class ForegroundNoticeDialogFragment : SelectDialogFragment() {
    lateinit var binding: DialogForegroundNoticeBinding

    companion object {
        const val TAG = "ForegroundNoticeDialogFragment"

        fun showConditionally(activity: FragmentActivity?, tag: String) {
            if (activity == null)
                return
            val installer = if (Build.VERSION.SDK_INT >= 30) {
                activity.packageManager.getInstallSourceInfo(activity.getPackageName()).installingPackageName
            } else {
                activity.packageManager.getInstallerPackageName(activity.getPackageName())
            }
            if (installer != null && !installer.contains("com.google") && !installer.contains("com.android")) {
                // No need to follow Google's rules if installed from Sunmi or pretix Marketplace
                return
            }

            val pref_key = "_foreground_notice_seen_$tag"
            val sp = PreferenceManager.getDefaultSharedPreferences(activity)

            if (sp.getBoolean(pref_key, false) && !BuildConfig.DEBUG) {
                // Show only once, except in debug mode
                return
            }

            ForegroundNoticeDialogFragment().show(activity.supportFragmentManager, "TAG")
            sp.edit {
                putBoolean(pref_key, true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binder = DialogForegroundNoticeBinding.inflate(layoutInflater, null, false)

        val dialog: AlertDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.foreground_service_dialog_title)
            .setView(binder.root)
            .create()

        binder.buttonOK.setOnClickListener { dismiss() }

        dialog.window?.setWindowAnimations(R.style.AppTheme_Dialog_ShortAnimation)
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
}