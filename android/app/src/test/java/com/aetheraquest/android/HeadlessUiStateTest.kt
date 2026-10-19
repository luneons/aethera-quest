package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HeadlessUiStateTest {
    private val login = LoginToken("demo-user", "sample-value")
    private val server = AqwServer("DemoServer", "sock7.aq.com", 5588, true, false, false)
    private val launch = HeadlessLaunch(login, server, "yulgar")

    @Test fun startingHeadlessKeepsTheJoinScreenUntilTheServiceReportsReady() {
        val current = UiState(screen = Screen.JOIN, username = "demo-user", selectedServer = server, mapName = "yulgar")
        val pending = headlessUiAfterServiceStart(current, launch)
        assertEquals(Screen.HEADLESS, pending.screen)
        assertEquals("demo-user", pending.username)
        assertEquals(server, pending.selectedServer)
        assertEquals("yulgar", pending.mapName)
        assertEquals(launch, pending.headlessLaunch)
        assertEquals("Starting headless mode…", pending.headlessStatus.message)
    }

    @Test fun aRunningHeadlessSessionStaysVisibleInTheApp() {
        val status = ConnectionStatus(ConnectionPhase.IN_MAP, "Connected to yulgar-14045", AreaSnapshot(mapName = "yulgar-14045"))
        val current = UiState(screen = Screen.HEADLESS, headlessLaunch = launch)
        val next = headlessUiFromStatus(current, status)
        assertEquals(Screen.HEADLESS, next.screen)
        assertEquals("yulgar-14045", next.headlessStatus.area.mapName)
        assertFalse(next.isLoading)
        assertNull(next.error)
    }

    @Test fun aFailedHeadlessSessionShowsTheFailureInsteadOfReturningToLogin() {
        val failed = ConnectionStatus(ConnectionPhase.DISCONNECTED, "Game server rejected login")
        val current = UiState(screen = Screen.HEADLESS, headlessLaunch = launch)
        val next = headlessUiFromStatus(current, failed)
        assertEquals(Screen.HEADLESS, next.screen)
        assertEquals("Game server rejected login", next.error)
        assertEquals("demo-user", next.headlessLaunch?.login?.username)
    }

    @Test fun stoppingHeadlessReturnsToTheJoinScreenWithoutLosingTheLoginSelection() {
        val current = UiState(screen = Screen.HEADLESS, headlessLaunch = launch, selectedServer = server, mapName = "yulgar")
        val next = headlessUiAfterStop(current)
        assertEquals(Screen.JOIN, next.screen)
        assertEquals(server, next.selectedServer)
        assertEquals("yulgar", next.mapName)
        assertNull(next.headlessLaunch)
    }
}
