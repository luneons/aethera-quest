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
)

data class MonsterState(
    val mapId: Int,
    val monsterId: Int? = null,
    val name: String? = null,
    val cell: String? = null,
    val hp: Int? = null,
    val maxHp: Int? = null,
    val state: Int? = null,
)

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
            "uotls", "tempSta" -> updatePlayer(
                obj.stringOrNull("unm") ?: obj.stringOrNull("uoName") ?: "", updates,
            )
            else -> false
        }
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
        if (old == null && !isSelf) return false
        val next = (old ?: PlayerState(selfUsername, selfUserId)).copy(
            cell = raw.stringOrNull("strFrame") ?: old?.cell,
            pad = raw.stringOrNull("strPad") ?: old?.pad,
            hp = raw.intOrNull("intHP") ?: old?.hp,
            maxHp = raw.intOrNull("intHPMax") ?: old?.maxHp,
            state = raw.intOrNull("intState") ?: old?.state,
            x = raw.intOrNull("tx") ?: old?.x,
            y = raw.intOrNull("ty") ?: old?.y,
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
            username, raw.intOrNull("uid") ?: raw.intOrNull("iUserID"),
            raw.stringOrNull("strFrame"), raw.stringOrNull("strPad"),
            raw.intOrNull("intHP"), raw.intOrNull("intHPMax"), raw.intOrNull("intState"),
            raw.intOrNull("tx"), raw.intOrNull("ty"),
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
private fun JSONObject.stringOrNull(key: String): String? = optString(key).trim().ifEmpty { null }
private fun JSONObject.arrayObjects(key: String): List<JSONObject> {
    val array: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull(array::optJSONObject)
}
private fun JSONObject.copyInto(target: JSONObject) { keys().forEach { target.put(it, get(it)) } }
