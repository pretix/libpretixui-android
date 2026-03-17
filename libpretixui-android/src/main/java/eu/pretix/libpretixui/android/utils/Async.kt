package eu.pretix.libpretixui.android.utils

import android.app.Dialog
import eu.pretix.libpretixui.android.BuildConfig
import io.sentry.Sentry
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.system.exitProcess

val crashLogger: (Throwable) -> Unit = { throwable: Throwable ->
    throwable.printStackTrace()
    if (BuildConfig.DEBUG) {
        exitProcess(1)
    } else {
        Sentry.captureException(throwable)
    }
}

fun <T> T.doAsyncSentry(
        exceptionHandler: ((Throwable) -> Unit)? = crashLogger,
        task: AnkoAsyncContext<T>.() -> Unit
): Future<Unit> {
    val context = AnkoAsyncContext(WeakReference(this))
    return BackgroundExecutor.submit {
        return@submit try {
            context.task()
        } catch (thr: Throwable) {
            val result = exceptionHandler?.invoke(thr)
            if (result != null) {
                result
            } else {
                Unit
            }
        }
    }
}
internal object BackgroundExecutor {
    var executor: ExecutorService =
            Executors.newScheduledThreadPool(2 * Runtime.getRuntime().availableProcessors())

    fun <T> submit(task: () -> T): Future<T> = executor.submit(task)
}

fun Dialog.safeDismiss() {
    try {
        dismiss()
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
    }
}

fun Dialog.safeCancel() {
    try {
        cancel()
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
    }
}
