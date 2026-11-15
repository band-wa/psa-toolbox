package com.bandwa.openadb.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.UUID

object FuelLedger {
    private const val TAG = "FuelLedger"
    private const val FILE_NAME = "fuel_ledger.jsonl"

    data class Entry(
        val id: String = "",
        val timestamp: Long,
        val amount: Int,
        val percentBefore: Int,
        val percentAfter: Int,
        val totalMileageKm: Float?,
        val manual: Boolean = false,
        val skipped: Boolean = false,
    )

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun append(context: Context, entry: Entry): Boolean {
        return try {
            val record = if (entry.id.isEmpty()) entry.copy(id = UUID.randomUUID().toString()) else entry
            val bytes = (entryToJson(record).toString() + "\n").toByteArray()
            java.io.FileOutputStream(file(context), true).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "append failed: ${e.javaClass.simpleName} ${e.message}")
            false
        }
    }

    private fun rewrite(context: Context, entries: List<Entry>): Boolean {
        val target = file(context)
        val tmp = File(target.parentFile, "$FILE_NAME.tmp")
        val text = entries.joinToString("") { entryToJson(it).toString() + "\n" }
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return false
            }
        }
        return true
    }

    @Synchronized
    fun updateAmount(context: Context, entry: Entry, amount: Int): Boolean {
        return try {
            val entries = readAll(context).map {
                if (it.id == entry.id) it.copy(amount = amount, skipped = false) else it
            }
            rewrite(context, entries)
        } catch (e: Exception) {
            Log.w(TAG, "updateAmount failed: ${e.javaClass.simpleName} ${e.message}")
            false
        }
    }

    @Synchronized
    fun delete(context: Context, entry: Entry): Boolean {
        return try {
            rewrite(context, readAll(context).filterNot { it.id == entry.id })
        } catch (e: Exception) {
            Log.w(TAG, "delete failed: ${e.javaClass.simpleName} ${e.message}")
            false
        }
    }

    private fun entryToJson(entry: Entry): JSONObject = JSONObject().apply {
        if (entry.id.isNotEmpty()) put("id", entry.id)
        put("ts", entry.timestamp)
        put("amount", entry.amount)
        put("percentBefore", entry.percentBefore)
        put("percentAfter", entry.percentAfter)
        entry.totalMileageKm?.takeIf { it.isFinite() }?.let { put("mileageKm", it.toDouble()) }
        if (entry.manual) put("manual", true)
        if (entry.skipped) put("skipped", true)
    }

    data class Segment(
        val km: Float,
        val liters: Float,
        val per100Km: Float,

        val costPerKm: Float?,
    )

    fun consumptionByEntry(entries: List<Entry>, tankLiters: Int): Map<String, Segment> {
        if (entries.size < 2 || tankLiters <= 0) return emptyMap()

        val usable = entries
            .sortedBy { it.timestamp }
            .filter {
                !it.manual &&
                    it.percentBefore in 0..100 &&
                    it.percentAfter in 0..100 &&
                    it.totalMileageKm != null
            }
        val result = HashMap<String, Segment>()
        for (i in 1 until usable.size) {
            val prev = usable[i - 1]
            val cur = usable[i]
            val prevMileage = prev.totalMileageKm ?: continue
            val curMileage = cur.totalMileageKm ?: continue
            if (!prevMileage.isFinite() || !curMileage.isFinite()) continue
            val km = curMileage - prevMileage
            val drop = prev.percentAfter - cur.percentBefore
            if (km <= 1f || drop <= 0) continue
            val liters = tankLiters * drop / 100f
            val per100Km = liters * 100f / km
            if (!per100Km.isFinite() || per100Km <= 0f || per100Km > 100f) continue
            val costPerKm = if (prev.amount > 0) prev.amount / km else null
            result[cur.id] = Segment(km, liters, per100Km, costPerKm)
        }
        return result
    }

    @Synchronized
    fun readAll(context: Context): List<Entry> {
        val f = file(context)
        if (!f.isFile) return emptyList()
        return try {
            f.readLines().mapIndexedNotNull { index, line ->
                if (line.isBlank()) return@mapIndexedNotNull null
                try {
                    val json = JSONObject(line)
                    val ts = json.optLong("ts")
                    val mileage = json.optDouble("mileageKm", Double.NaN).toFloat()
                    Entry(
                        id = json.optString("id").ifEmpty { "legacy-$ts-$index" },
                        timestamp = ts,
                        amount = json.optInt("amount"),
                        percentBefore = json.optInt("percentBefore"),
                        percentAfter = json.optInt("percentAfter"),
                        totalMileageKm = mileage.takeIf { it.isFinite() },
                        manual = json.optBoolean("manual", false),
                        skipped = json.optBoolean("skipped", false),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "skip bad line: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "read failed: ${e.javaClass.simpleName} ${e.message}")
            emptyList()
        }
    }
}
