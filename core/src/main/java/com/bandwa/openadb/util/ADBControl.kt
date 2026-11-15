package com.bandwa.openadb.util

object ADBControl {
    private const val ADB_TCP_PORT_KEY = "service.adb.tcp.port"
    private const val ADB_TCP_PORT_OPEN_VALUE = "5555"
    private const val ADB_TCP_PORT_CLOSE_VALUE = "0"
    private const val STOP_ADB_KEY = "ctl.stop"
    private const val STOP_ADB_VALUE = "adbd"
    private const val START_ADB_KEY = "ctl.start"
    private const val START_ADB_VALUE = "adbd"

    fun openADB() {
        ShellUtils.exec("setprop $ADB_TCP_PORT_KEY $ADB_TCP_PORT_OPEN_VALUE")
        ShellUtils.exec("setprop $STOP_ADB_KEY $STOP_ADB_VALUE")
        ShellUtils.exec("setprop $START_ADB_KEY $START_ADB_VALUE")
    }

    fun closeADB() {
        ShellUtils.exec("setprop $ADB_TCP_PORT_KEY $ADB_TCP_PORT_CLOSE_VALUE")
        ShellUtils.exec("setprop $STOP_ADB_KEY $STOP_ADB_VALUE")
        ShellUtils.exec("setprop $START_ADB_KEY $START_ADB_VALUE")
    }

    fun isAdbWirelessEnabled(): Boolean {
        val adbTcpPort = ShellUtils.exec("getprop $ADB_TCP_PORT_KEY").output.trim()
        return adbTcpPort == ADB_TCP_PORT_OPEN_VALUE
    }
}
