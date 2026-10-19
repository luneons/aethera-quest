package com.aetheraquest.android

import org.json.JSONArray
import org.json.JSONObject

data class PlayerState(
    val username: String,
    val userId: Int? = null,
    val cell: String? = null,
    val pad: String? = null,
    val hp: Int? = null,
    val maxHp: Int? = null,
    val state: Int? = null,
    val x: Int? = null,
    val y: Int? = null,
    val mp: Int? = null,
    val maxMp: Int? = null,
    /**
     * Set from the authoritative death signals: `playerDeath` or an empty
     * `"p":{...:{}}` entry, which is how the server reports a death before it
     * sends any HP numbers. Cleared by the next update carrying live HP.
     */
    val dead: Boolean = false,
)

data class MonsterState(
    val mapId: Int,
    val monsterId: Int? = null,
    val name: String? = null,
    val cell: String? = null,
    val hp: Int? = null,
    val maxHp: Int? = null,
    val state: Int? = null,
) {
    /**
     * AQW reports `intState: 0` (or zero HP) once a monster dies. It stays in
     * the area snapshot so the same MonMapID can come back on respawn, but the
     * UI must not offer it as an attack target.
     */
    val alive: Boolean
        get() = (hp ?: 0) > 0 && (state ?: 1) != 0
}

data class AreaSnapshot(
    val generation: Long = 0,
    val roomId: Int? = null,
    val mapName: String? = null,
    val self: PlayerState? = null,
    val players: Map<String, PlayerState> = emptyMap(),
    val monsters: Map<Int, MonsterState> = emptyMap(),
    /**
     * True only when the coordinates in [self] came from the server (`tx`/`ty`).
     * A locally predicted position is useful for the radar but must not be
     * presented as if the server confirmed it.
     */
    val selfPositionServerObserved: Boolean = false,
)

class AreaReducer(private val selfUsername: String, private var selfUserId: Int? = null) {
    private var state = AreaSnapshot()

    @Synchronized fun setSelfUserId(value: Int?) { selfUserId = value }

    @Synchronized fun setMapState(snapshot: AreaSnapshot) { state = snapshot }
    @Synchronized fun snapshot(): AreaSnapshot = state.copy(
        players = state.players.toMap(), monsters = state.monsters.toMap(),
    )

    @Synchronized fun noteLocalCell(cell: String, pad: String) {
        val me = state.self ?: return
        val next = me.copy(cell = cell, pad = pad.ifBlank { "Spawn" })
        state = state.copy(self = next, players = state.players + (me.username.lowercase() to next))
    }

    @Synchronized fun noteLocalPosition(x: Int, y: Int) {
        val me = state.self ?: return
        val next = me.copy(x = x, y = y)
        state = state.copy(
            self = next,
            players = state.players + (me.username.lowercase() to next),
            selfPositionServerObserved = false,
        )
    }

    @Synchronized fun feed(packet: String, generation: Long? = null): Boolean {
        if (generation != null && generation != state.generation) return false
        SfsProtocol.parseJsonPacket(packet)?.let { return feedJson(it) }
        return SfsProtocol.parseStringPacket(packet)?.let { feedString(it) } ?: false
    }

    private fun feedJson(packet: JsonPacket): Boolean {
        val obj = packet.payload
        if (packet.command == "moveToArea") {
            val definitions = obj.arrayObjects("mondef").associateBy { it.intOrNull("MonID") }
            val maps = obj.arrayObjects("monmap").associateBy { it.intOrNull("MonMapID") }
            val monsters = mutableMapOf<Int, MonsterState>()
            obj.arrayObjects("monBranch").forEach { branch ->
                val mapId = branch.intOrNull("MonMapID") ?: return@forEach
                val raw = JSONObject()
                definitions[branch.intOrNull("MonID")]?.copyInto(raw)
                maps[mapId]?.copyInto(raw)
                branch.copyInto(raw)
                monsters[mapId] = monster(raw, mapId)
            }
            val players = obj.arrayObjects("uoBranch").mapNotNull { player(it) }
                .associateBy { it.username.lowercase() }
            val me = players[selfUsername.lowercase()] ?: players.values.firstOrNull {
                selfUserId != null && it.userId == selfUserId
            }
            state = AreaSnapshot(
                generation = state.generation + 1,
                roomId = obj.intOrNull("areaId") ?: packet.room,
                mapName = obj.stringOrNull("areaName"),
                self = me,
                players = players,
                monsters = monsters,
                selfPositionServerObserved = me?.x != null && me.y != null,
            )
            return true
        }
        val updates = obj.optJSONObject("o") ?: obj
        return when (packet.command) {
            "mtls" -> updateMonster(obj.intOrNull("id"), updates)
            // Combat results. The server reports both sides keyed by MonMapID /
            // username, so a hit is reflected immediately instead of leaving the
            // monster frozen at the HP it had when the map loaded.
            "ct" -> updateCombat(obj)
            "uotls", "tempSta" -> updatePlayer(
                obj.stringOrNull("unm") ?: obj.stringOrNull("uoName") ?: "", updates,
            )
            "playerDeath" -> handlePlayerDeath(obj)
            else -> false
        }
    }

    /** Applies the `m` (monsters) and `p` (players) maps of a `ct` packet. */
    private fun updateCombat(obj: JSONObject): Boolean {
        var changed = false
        obj.optJSONObject("m")?.let { monsters ->
            val next = state.monsters.toMutableMap()
            monsters.keys().forEach { key ->
                val mapId = key.toIntOrNull() ?: return@forEach
                val old = next[mapId] ?: return@forEach
                val raw = monsters.optJSONObject(key) ?: return@forEach
                next[mapId] = old.copy(
                    hp = raw.intOrNull("intHP") ?: old.hp,
                    maxHp = raw.intOrNull("intHPMax") ?: old.maxHp,
                    state = raw.intOrNull("intState") ?: old.state,
                )
                changed = true
            }
            if (changed) state = state.copy(monsters = next)
        }
        obj.optJSONObject("p")?.let { players ->
            players.keys().forEach { name ->
                val raw = players.optJSONObject(name) ?: return@forEach
                // An empty player object is the server's death notice: the
                // killing blow arrives before any HP numbers do.
                val touched = if (raw.length() == 0) markPlayerDead(name) else updatePlayer(name, raw)
                if (touched) changed = true
            }
        }
        return changed
    }

    /** `playerDeath` names the victim by user id; only self can be revived here. */
    private fun handlePlayerDeath(obj: JSONObject): Boolean {
        val victimId = obj.intOrNull("userID") ?: obj.intOrNull("uid") ?: obj.intOrNull("iUserID")
        val self = state.self ?: return false
        if (victimId != null && self.userId != null && victimId != self.userId) return false
        return markPlayerDead(self.username)
    }

    private fun markPlayerDead(name: String): Boolean {
        val key = state.players.entries.firstOrNull { it.key.equals(name.trim(), true) }?.key
            ?: state.self?.username?.lowercase()
            ?: return false
        val current = state.players[key] ?: state.self ?: return false
        if (current.dead && (current.hp ?: 0) <= 0) return false
        val next = current.copy(hp = 0, state = 0, dead = true)
        state = state.copy(
            players = state.players + (key to next),
            self = if (key == state.self?.username?.lowercase()) next else state.self,
        )
        return true
    }

    private fun feedString(packet: StringPacket): Boolean = when (packet.command) {
        "mtls" -> packet.args.getOrNull(0)?.toIntOrNull()?.let {
            updateMonster(it, parseUpdates(packet.args.getOrNull(1).orEmpty()))
        } ?: false
        "uotls", "tempSta" -> if (packet.args.size >= 2) {
            updatePlayer(packet.args[0], parseUpdates(packet.args[1]))
        } else false
        "exitArea" -> {
            val key = packet.args.getOrNull(1)?.trim()?.lowercase()
                ?: packet.args.firstOrNull()?.toIntOrNull()?.let { id ->
                    state.players.entries.firstOrNull { it.value.userId == id }?.key
                }
            if (key == null || key !in state.players) false else {
                state = state.copy(players = state.players - key)
                true
            }
        }
        "respawnMon" -> {
            var changed = false
            val monsters = state.monsters.toMutableMap()
            packet.args.firstOrNull().orEmpty().split(',').mapNotNull(String::toIntOrNull).forEach { id ->
                monsters[id]?.let { monsters[id] = it.copy(hp = it.maxHp, state = 1); changed = true }
            }
            if (changed) state = state.copy(monsters = monsters)
            changed
        }
        else -> false
    }

    private fun updateMonster(id: Int?, raw: JSONObject): Boolean {
        val old = state.monsters[id] ?: return false
        val next = old.copy(
            monsterId = raw.intOrNull("MonID") ?: old.monsterId,
            name = raw.stringOrNull("strMonName") ?: old.name,
            cell = raw.stringOrNull("strFrame") ?: old.cell,
            hp = raw.intOrNull("intHP") ?: old.hp,
            maxHp = raw.intOrNull("intHPMax") ?: old.maxHp,
            state = raw.intOrNull("intState") ?: old.state,
        )
        state = state.copy(monsters = state.monsters + (old.mapId to next))
        return true
    }

    private fun updatePlayer(name: String, raw: JSONObject): Boolean {
        val requestedKey = name.trim().lowercase()
        if (requestedKey.isEmpty()) return false
        val idFromUpdate = raw.intOrNull("uid") ?: raw.intOrNull("iUserID")
        val key = when {
            requestedKey in state.players -> requestedKey
            idFromUpdate != null -> state.players.entries.firstOrNull { it.value.userId == idFromUpdate }?.key ?: requestedKey
            else -> requestedKey
        }
        val old = state.players[key]
        val isSelf = key == selfUsername.lowercase() || requestedKey == selfUsername.lowercase() ||
            (old?.userId != null && old.userId == selfUserId) || (idFromUpdate != null && idFromUpdate == selfUserId)
        // A `uotls` after the initial `moveToArea` is how newcomers arrive: the
        // server names one player at a time, including someone who was never in
        // the snapshot. Refusing them keeps the list frozen at join time.
        val next = (old ?: PlayerState(
            raw.stringOrNull("strUsername")?.ifBlank { null }
                ?: raw.stringOrNull("uoName")?.ifBlank { null }
                ?: name,
            idFromUpdate ?: selfUserId.takeIf { isSelf },
        )).copy(
            cell = raw.stringOrNull("strFrame") ?: old?.cell,
            pad = raw.stringOrNull("strPad") ?: old?.pad,
            hp = raw.intOrNull("intHP") ?: old?.hp,
            maxHp = raw.intOrNull("intHPMax") ?: old?.maxHp,
            state = raw.intOrNull("intState") ?: old?.state,
            x = raw.intOrNull("tx") ?: old?.x,
            y = raw.intOrNull("ty") ?: old?.y,
            mp = raw.intOrNull("intMP") ?: old?.mp,
            maxMp = raw.intOrNull("intMPMax") ?: old?.maxMp,
            // A live HP number is the server saying "alive again"; without this
            // the death latch would never clear and respawn would keep firing.
            dead = if ((raw.intOrNull("intHP") ?: 0) > 0) false else old?.dead ?: false,
        )
        state = state.copy(
            players = state.players + (key to next),
            self = if (isSelf) next else state.self,
            selfPositionServerObserved = when {
                !isSelf -> state.selfPositionServerObserved
                raw.intOrNull("tx") != null && raw.intOrNull("ty") != null -> true
                else -> state.selfPositionServerObserved
            },
        )
        return true
    }

    private fun player(raw: JSONObject): PlayerState? {
        val username = raw.stringOrNull("uoName") ?: raw.stringOrNull("strUsername") ?: return null
        return PlayerState(
            username, raw.playerId(),
            raw.stringOrNull("strFrame"), raw.stringOrNull("strPad"),
            raw.intOrNull("intHP"), raw.intOrNull("intHPMax"), raw.intOrNull("intState"),
            raw.intOrNull("tx"), raw.intOrNull("ty"),
            raw.intOrNull("intMP"), raw.intOrNull("intMPMax"),
        )
    }

    private fun monster(raw: JSONObject, mapId: Int) = MonsterState(
        mapId, raw.intOrNull("MonID"), raw.stringOrNull("strMonName"),
        raw.stringOrNull("strFrame"), raw.intOrNull("intHP"),
        raw.intOrNull("intHPMax"), raw.intOrNull("intState"),
    )

    private fun parseUpdates(value: String): JSONObject = JSONObject().also { out ->
        value.split(',').forEach { part ->
            val split = part.indexOf(':')
            if (split > 0) {
                val key = part.substring(0, split)
                val text = part.substring(split + 1)
                if (key.lowercase().startsWith("int") || key in setOf("tx", "ty", "sp")) {
                    out.put(key, text.toIntOrNull() ?: text)
                } else out.put(key, text)
            }
        }
    }
}

private fun JSONObject.intOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else
    opt(key)?.toString()?.toIntOrNull()

/**
 * Player id across the shapes the server uses: `uoBranch` names it `entID`,
 * while `uotls`/`playerDeath` use `uid` or `iUserID`. Missing this made
 * `self.userId` null, and without a uid `resPlayerTimed` can never be addressed.
 */
private fun JSONObject.playerId(): Int? =
    intOrNull("entID") ?: intOrNull("uid") ?: intOrNull("iUserID") ?: intOrNull("userID")
private fun JSONObject.stringOrNull(key: String): String? = optString(key).trim().ifEmpty { null }
private fun JSONObject.arrayObjects(key: String): List<JSONObject> {
    val array: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull(array::optJSONObject)
}
private fun JSONObject.copyInto(target: JSONObject) { keys().forEach { target.put(it, get(it)) } }
