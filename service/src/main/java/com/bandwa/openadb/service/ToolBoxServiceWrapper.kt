package com.bandwa.openadb.service

interface ToolBoxServiceWrapper {
    fun startService(action: String? = null)

    companion object{
        const val ACTION_START_PROXY_AIR_CONDITION = "action_start_proxy_air_condition"
        const val ACTION_STOP_PROXY_AIR_CONDITION = "action_stop_proxy_air_condition"
        const val ACTION_START_PROJECTION = "action_start_projection"
        const val ACTION_STOP_PROJECTION = "action_stop_projection"
        const val ACTION_START_INDEPENDENT_PROJECTION = "action_start_independent_projection"
        const val ACTION_STOP_INDEPENDENT_PROJECTION = "action_stop_independent_projection"
        const val ACTION_START_FULLSCREEN_GESTURE = "action_start_fullscreen_gesture"
        const val ACTION_STOP_FULLSCREEN_GESTURE = "action_stop_fullscreen_gesture"
    }
}
