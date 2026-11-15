package com.bandwa.openadb.util

import java.util.concurrent.ConcurrentHashMap

object HookState {
    private val versions = ConcurrentHashMap<String, String>()

    fun report(packageName: String, version: String) {
        versions[packageName] = version
    }

    fun forget(packageName: String) {
        versions.remove(packageName)
    }

    fun versionOf(packageName: String): String? = versions[packageName]
}
