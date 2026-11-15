package com.bandwa.openadb.presentation.main.fuel

import com.bandwa.openadb.util.FuelLedger
import com.bandwa.openadb.util.Utils

data class FuelLedgerUIState(
    val entries: List<FuelLedger.Entry> = emptyList(),

    val segments: Map<String, FuelLedger.Segment> = emptyMap(),
    val totalAmount: Int = 0,
    val averageAmount: Int = 0,
    val averagePer100Km: Float? = null,
    val averageCostPerKm: Float? = null,

    val promptEnabled: Boolean = false,

    val tankLiters: Int = Utils.FUEL_TANK_DEFAULT,
)
