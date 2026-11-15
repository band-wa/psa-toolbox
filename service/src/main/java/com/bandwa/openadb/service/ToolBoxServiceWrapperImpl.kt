package com.bandwa.openadb.service

import android.content.Context
import android.content.Intent
import com.bandwa.openadb.service.PSAToolBoxService.Companion.EXTRA_ACTION_KEY
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ToolBoxServiceWrapperImpl @Inject constructor(
    @ApplicationContext private val applicationContext: Context
) : ToolBoxServiceWrapper {
    override fun startService(action: String?) {
        val intent = Intent(applicationContext, PSAToolBoxService::class.java).apply {
            action?.let {
                putExtra(EXTRA_ACTION_KEY, action)
            }
        }
        applicationContext.startService(intent)
    }
}
