package com.bandwa.openadb.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HvacWindowEventHubTest {
    @After
    fun tearDown() {
        HvacWindowEventHub.onServiceDisconnected()
    }

    @Test
    fun connectionListeners_receiveStateChanges() {
        val received = mutableListOf<Boolean>()
        val listener: (Boolean) -> Unit = { received.add(it) }
        HvacWindowEventHub.addConnectionListener(listener)
        try {
            HvacWindowEventHub.onServiceConnected()
            assertTrue(HvacWindowEventHub.connected)
            HvacWindowEventHub.onServiceDisconnected()
            assertFalse(HvacWindowEventHub.connected)
            assertEquals(listOf(true, false), received)
        } finally {
            HvacWindowEventHub.removeConnectionListener(listener)
        }
    }

    @Test
    fun windowListeners_receiveWindowChanges() {
        var count = 0
        val listener: () -> Unit = { count++ }
        HvacWindowEventHub.addListener(listener)
        try {
            HvacWindowEventHub.onWindowChanged()
            assertEquals(1, count)

            HvacWindowEventHub.onServiceConnected()
            assertEquals(2, count)
        } finally {
            HvacWindowEventHub.removeListener(listener)
        }
    }
}
