package com.bandwa.openadb.presentation.main.binding

import android.content.Context
import com.bandwa.openadb.util.AppPick
import com.bandwa.openadb.util.PickApp
import com.bandwa.openadb.util.Utils.getSystemProp
import com.bandwa.openadb.util.Utils.setSystemProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppKeyBindingState(
    val shortClickApp: PickApp? = null,
    val longClickApp: PickApp? = null,
    val installedApps: List<PickApp> = emptyList(),
)

object AppKeyBindingStore {
    fun read(context: Context, shortClickProp: String, longClickProp: String): AppKeyBindingState =
        AppKeyBindingState(
            shortClickApp = PickApp.of(context, getSystemProp(shortClickProp)),
            longClickApp = PickApp.of(context, getSystemProp(longClickProp)),
        )

    fun save(context: Context, propKey: String, target: String): PickApp? {
        setSystemProp(propKey, target)
        return PickApp.of(context, target)
    }

    suspend fun loadInstalledApps(context: Context): List<PickApp> =
        withContext(Dispatchers.IO) { AppPick.list(context) }
}
