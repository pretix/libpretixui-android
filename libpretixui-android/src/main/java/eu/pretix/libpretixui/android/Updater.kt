package eu.pretix.libpretixui.android

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.ProgressDialog
import android.content.*
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import java.io.File

const val FILEPROVIDERAUTHORITY = BuildConfig.LIBRARY_PACKAGE_NAME + ".fileprovider"

class Updater(val appname: String, val ctx: Context, val activity: Activity) {
    fun startUpdate() {
        if (installedByGMS()) {
            gmsUpdate()
        } else {
            nongmsUpdate()
        }
    }

    private fun installedByGMS(): Boolean {
        val gmsinstallers = listOf("com.android.vending", "com.google.android.feedback")
        val installer = ctx.packageManager.getInstallerPackageName(ctx.packageName)

        return installer != null && gmsinstallers.contains(installer)
    }

    private fun gmsUpdate() {
        val appUpdateManager = AppUpdateManagerFactory.create(ctx)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            when (appUpdateInfo.updateAvailability()) {
                UpdateAvailability.UPDATE_AVAILABLE -> {
                    appUpdateManager.startUpdateFlow(
                        appUpdateInfo,
                        activity,
                        AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE)
                    )
                }
                UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                    AlertDialog.Builder(ctx)
                        .setMessage(ctx.getString(R.string.updater_no_update_available))
                        .create()
                        .show()
                }
                UpdateAvailability.UNKNOWN -> {
                    AlertDialog.Builder(ctx)
                        .setMessage(ctx.getString(R.string.updater_update_unknown))
                        .create()
                        .show()
                }
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    TODO()
                }
            }

            appUpdateInfoTask.addOnFailureListener {
                AlertDialog.Builder(ctx)
                    .setMessage(ctx.getString(R.string.updater_update_unknown))
                    .create()
                    .show()
            }
        }

        appUpdateInfoTask.addOnCompleteListener {
            if (appUpdateInfoTask.isComplete && !appUpdateInfoTask.isSuccessful) {
                AlertDialog.Builder(ctx)
                    .setMessage(ctx.getString(R.string.updater_update_unknown))
                    .create()
                    .show()
            }
        }
    }

    private fun nongmsUpdate() {
        val manager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse("https://marketplace.pretix.eu/download/%s/latest".format(appname))
        val filename = "%s.apk".format(appname)
        val request = DownloadManager.Request(uri)
        request.setMimeType("application/vnd.android.package-archive")
        request.allowScanningByMediaScanner()
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        manager.enqueue(request)

        val progressBar = ProgressDialog.show(
            ctx,
            ctx.getString(R.string.updater_downloading_title),
            ctx.getString(R.string.updater_downloading_body),
            true,
            false
        )


        class DownloadReceiver : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                progressBar.dismiss()
                val downloadId = intent!!.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
                val query = DownloadManager.Query()
                query.setFilterById(downloadId)
                val cursor = manager.query(query)
                if (cursor.moveToFirst()) {
                    var downloadedFile = Uri.parse(cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)))
                    if (ContentResolver.SCHEME_FILE == downloadedFile.scheme) {
                        val file = File(downloadedFile.path)
                        downloadedFile = FileProvider.getUriForFile(context!!, FILEPROVIDERAUTHORITY, file)
                    }

                    val installIntent = Intent(Intent.ACTION_VIEW)
                    installIntent.setDataAndType(downloadedFile, "application/vnd.android.package-archive")
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP + Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context!!.startActivity(installIntent)
                }
                cursor.close()

                context!!.unregisterReceiver(this)
            }
        }
        ctx.registerReceiver(DownloadReceiver(), IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
}