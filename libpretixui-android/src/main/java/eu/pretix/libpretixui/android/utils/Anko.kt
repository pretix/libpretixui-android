package eu.pretix.libpretixui.android.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import java.lang.ref.WeakReference

// contains excerpts from https://github.com/Kotlin/anko

/*
 * Copyright 2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

private object ContextHelper {
    val handler = Handler(Looper.getMainLooper())
}

/**
 * Execute [f] on the application UI thread.
 */
fun Context.runOnUiThread(f: Context.() -> Unit) {
    if (Looper.getMainLooper() === Looper.myLooper()) f() else ContextHelper.handler.post { f() }
}

inline fun Fragment.runOnUiThread(crossinline f: () -> Unit) {
    requireActivity().runOnUiThread { f() }
}

class AnkoAsyncContext<T>(val weakRef: WeakReference<T>)

/**
 * Execute [f] on the application UI thread if the underlying [Activity] still exists and is not finished.
 * The receiver [Activity] will be passed to [f].
 *  If it is not exist anymore or if it was finished, [f] will not be called.
 */
fun <T : Activity> AnkoAsyncContext<T>.activityUiThread(f: (T) -> Unit): Boolean {
    val activity = weakRef.get() ?: return false
    if (activity.isFinishing) return false
    activity.runOnUiThread { f(activity) }
    return true
}
