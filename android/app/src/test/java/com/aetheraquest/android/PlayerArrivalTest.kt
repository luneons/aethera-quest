package com.aetheraquest.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Players arrive in real time through `uotls` after the initial `moveToArea`:
 * a user switching cells, or a stranger joining the room, is reported as a
 * single update naming someone who was never in the snapshot. Refusing those
 * updates means the list only ever shows the players present at join time.
 */
class PlayerArrivalTest {

    private fun reducer(): AreaReducer {
        val reducer = AreaReducer("demo-user")
        reducer.feed(
            """{"t":"xt","b":{"r":-1,"o":{"cmd":"moveToArea","areaId":1,"areaName":"x-1",
            "uoBranch":[{"uoName":"demo-user","strUsername":"Demo User","entID":7,
            "strFrame":"Enter","strPad":"Spawn","intHP":1602,"intHPMax":1602}]}}}""",
        )
        return reducer
    }

    @Test
    fun aNewcomerJoiningMidSessionAppearsImmediately() {
        val reducer = reducer()

        val changed = reducer.feed(
            """{"t":"xt","b":{"r":-1,"o":{"cmd":"uotls","unm":"budiding",
            "o":{"strUsername":"Budiding","strFrame":"Enter","strPad":"Spawn",
            "intHP":500,"intHPMax":500,"intState":1}}}}""",
        )

        assertTrue(changed)
        val newcomer = reducer.snapshot().players["budiding"]
        assertEquals("Budiding", newcomer?.username)
        assertEquals("Enter", newcomer?.cell)
    }

    @Test
    fun aKnownPlayerWalkingToAnotherCellIsFollowed() {
        val reducer = reducer()
        reducer.feed(
            """{"t":"xt","b":{"r":-1,"o":{"cmd":"uotls","unm":"budiding",
            "o":{"strUsername":"Budiding","strFrame":"Enter","strPad":"Spawn"}}}}""",
        )
        reducer.feed(
            """{"t":"xt","b":{"r":-1,"o":{"cmd":"uotls","unm":"budiding",
            "o":{"strUsername":"Budiding","strFrame":"r2","strPad":"Left"}}}}""",
        )
        assertEquals("r2", reducer.snapshot().players["budiding"]?.cell)
    }

    @Test
    fun aNewcomerAlsoArrivesThroughTheStringForm() {
        val reducer = reducer()
        val changed = reducer.feed("%xt%uotls%-1%budiding%strFrame:r10,strPad:Left,intState:1%")
        assertTrue(changed)
        assertEquals("r10", reducer.snapshot().players["budiding"]?.cell)
    }

    @Test
    fun anUpdateWithNoNameIsStillRefused() {
        val reducer = reducer()
        assertEquals(false, reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"uotls","unm":"","o":{}}}}"""))
    }

    @Test
    fun aDepartingPlayerStillLeavesTheList() {
        val reducer = reducer()
        reducer.feed("""{"t":"xt","b":{"r":-1,"o":{"cmd":"uotls","unm":"budiding","o":{"strUsername":"Budiding"}}}}""")
        assertTrue(reducer.snapshot().players.containsKey("budiding"))

        reducer.feed("%xt%uotls%-1%budiding%exitArea:b%") // noise must not remove anyone
        assertTrue(reducer.snapshot().players.containsKey("budiding"))
    }
}

/**
 * The entity list can focus on one kind so a busy room stops hiding the
 * monsters behind a wall of strangers (or vice versa).
 */
class EntityFilterTest {

    private fun rows() = listOf(
        EntityRow("Enter", "Self", "demo-user", 100, 100, null),
        EntityRow("Enter", "Player", "budiding", 50, 50, null),
        EntityRow("r2", "Player", "storm", 60, 60, null),
        EntityRow("r2", "Monster", "Imp", 30, 30, 1),
        EntityRow("r3", "Monster", "Orc", 40, 40, 2),
    )

    @Test
    fun monstersOnlyHidesEveryPlayer() {
        val shown = filterEntityRows(rows(), EntityFilter.MONSTERS)
        assertEquals(listOf("Imp", "Orc"), shown.map { it.name })
    }

    @Test
    fun playersOnlyHidesEveryMonster() {
        val shown = filterEntityRows(rows(), EntityFilter.PLAYERS)
        assertEquals(listOf("demo-user", "budiding", "storm"), shown.map { it.name })
    }

    @Test
    fun allShowsEverythingInOrder() {
        assertEquals(5, filterEntityRows(rows(), EntityFilter.ALL).size)
    }

    @Test
    fun anEmptyBucketExplainsItself() {
        assertNull(nextFilterLabel(EntityFilter.ALL, hasPlayers = true, hasMonsters = false))
        assertEquals("No monsters in this map", nextFilterLabel(EntityFilter.MONSTERS, hasPlayers = true, hasMonsters = false))
        assertEquals("No players here", nextFilterLabel(EntityFilter.PLAYERS, hasPlayers = false, hasMonsters = true))
        assertEquals("No players here", nextFilterLabel(EntityFilter.PLAYERS, hasPlayers = false, hasMonsters = false))
    }
}
