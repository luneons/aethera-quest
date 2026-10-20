package com.aetheraquest.android

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object AqwConfig {
    const val LOGIN_URL = "https://game.aq.com/game/api/login/now"
    const val SERVER_LIST_URL = "https://content.aq.com/game/api/data/servers"
    const val SFS_VERSION = "157"
    const val LOGIN_ZONE = "zone_master"
    const val CLIENT_TOKEN = "SPIDER#0001"
    const val CLIENT_VERSION = "5.1"

    private val trustedGameHosts = setOf(
        "sock7.aq.com",
        "sock8.aq.com",
        "euro.aqw.artix.com",
        "asia.game.artix.com",
    )

    fun isTrustedGameHost(host: String): Boolean =
        host.trim().trimEnd('.').lowercase() in trustedGameHosts
}

class PacketFramer {
    private val buffer = ByteArrayOutputStream()

    @Synchronized fun feed(chunk: ByteArray): List<String> {
        val output = mutableListOf<String>()
        for (byte in chunk) {
            if (byte.toInt() == 0) {
                val packet = buffer.toByteArray()
                buffer.reset()
                if (packet.isNotEmpty()) {
                    val decoder = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    output += decoder.decode(ByteBuffer.wrap(packet)).toString()
                }
            } else {
                buffer.write(byte.toInt())
            }
        }
        return output
    }

    @Synchronized fun reset() { buffer.reset() }
}

object SfsProtocol {
    fun frame(text: String): ByteArray = text.toByteArray(StandardCharsets.UTF_8) + byteArrayOf(0)

    private fun xml(action: String, room: Int, body: String = "") =
        frame("<msg t='sys'><body action='$action' r='$room'>$body</body></msg>")

    fun verCheck() = xml("verChk", 0, "<ver v='${AqwConfig.SFS_VERSION}' />")

    fun login(username: String, token: String): ByteArray {
        val nick = "${AqwConfig.CLIENT_TOKEN}~${username.lowercase()}~${AqwConfig.CLIENT_VERSION}"
        return xml(
            "login", 0,
            "<login z='${AqwConfig.LOGIN_ZONE}'><nick><![CDATA[$nick]]></nick>" +
                "<pword><![CDATA[$token]]></pword></login>",
        )
    }

    fun getRoomList() = xml("getRmList", -1)
    fun logout(room: Int) = xml("logout", room)
    fun roundTrip(room: Int) = xml("roundTrip", room)

    fun xt(name: String, command: String, room: Int, vararg args: Any?): ByteArray = frame(
        buildString {
            append("%xt%").append(name).append('%').append(command).append('%').append(room).append('%')
            args.forEach { append(it ?: "").append('%') }
        },
    )

    fun firstJoin() = xt("zm", "firstJoin", 1)
    fun retrieveInventory(room: Int, userId: Int) = xt("zm", "retrieveInventory", room, userId)
    fun join(username: String, map: String) = xt("zm", "cmd", 1, "tfer", username.lowercase(), map)
    fun move(room: Int, x: Int, y: Int, speed: Int = 10) = xt("zm", "mv", room, x, y, speed)
    fun moveToCell(room: Int, cell: String, pad: String = "Spawn") =
        xt("zm", "moveToCell", room, cell.trim(), pad.trim().ifEmpty { "Spawn" })

    fun chat(room: Int, message: String, channel: String = "zone") =
        xt("zm", "message", room, message, channel.trim().ifEmpty { "zone" })

    /** AFK toggle is always addressed to room 1, not the current area. */
    fun afk(enabled: Boolean) = xt("zm", "afk", 1, if (enabled) "true" else "false")

    /** Timed player respawn is addressed to the current room with the live UID. */
    fun respawnPlayer(room: Int, userId: Int) = xt("zm", "resPlayerTimed", room, userId)

    /** Equips one owned item by its ItemID; the server replies with new `bEquip` flags. */
    fun equipItem(room: Int, itemId: Int) = xt("zm", "equipItem", room, itemId)

    private val combatActions = setOf("aa", "a1", "a2", "a3", "a4", "i1")

    /**
     * One combat action, serialized like `World.getActionResult`: the sequence
     * counter is a rolling 0..30 client value, targets use the reference-client
     * forms `m:<MonMapID>` and `p:<uid>`, and the trailing `wvz` is required.
     *
     * The consumable slot `i1` is special: it carries its assigned ItemID as an
     * extra argument before `wvz`. Omitting it makes the server ignore the cast,
     * which is why a taunt scroll would silently do nothing.
     */
    fun attack(
        room: Int,
        actionId: Int,
        action: String,
        vararg targets: String,
        itemId: Int? = null,
    ): ByteArray {
        require(actionId in 0..30) { "Combat sequence must be 0..30" }
        require(action in combatActions) { "Unsupported combat action: $action" }
        require(targets.isNotEmpty()) { "A combat action needs a target" }
        targets.forEach { require(Regex("^[mp]:[1-9]\\d*$").matches(it)) { "Invalid combat target: $it" } }
        if (action == "i1") {
            require(itemId != null && itemId > 0) { "Consumable i1 needs a positive item id" }
        } else {
            require(itemId == null) { "Only i1 carries an item id" }
        }
        val args = buildList {
            add(actionId)
            add(targets.joinToString(",") { "$action>$it" })
            itemId?.let { add(it) }
            add("wvz")
        }
        // Combat is sent to shared room 1 (reference client + aqw-python2 both
        // use `%xt%zm%gar%1%...`), not the per-area room id.
        return xt("zm", "gar", 1, *args.toTypedArray())
    }

    /** Uses an inventory consumable directly (`serverUseItem`). */
    fun useItem(room: Int, itemId: Int): ByteArray {
        require(itemId > 0) { "Missing item id" }
        return xt("zm", "serverUseItem", room, "+", itemId)
    }

    fun parseChat(text: String): ChatMessage? {
        val parts = text.split('%')
        if (parts.size < 8 || parts[1] != "xt" || parts[2] != "chatm") return null
        val channelMessage = parts[4]
        val separator = channelMessage.indexOf('~')
        if (separator <= 0) return null
        val userId = parts[6].toIntOrNull() ?: return null
        return ChatMessage(
            channel = channelMessage.substring(0, separator),
            message = channelMessage.substring(separator + 1),
            sender = parts[5],
            userId = userId,
        )
    }

    fun parseStringPacket(text: String): StringPacket? {
        if (!text.startsWith("%xt%")) return null
        val parts = text.split('%')
        if (parts.size < 5 || parts[1] != "xt") return null
        if (parts[2] != "zm") {
            val room = parts.getOrNull(3)?.toIntOrNull() ?: -1
            return StringPacket("", parts[2], room, parts.drop(4).dropLastWhile { it.isEmpty() })
        }
        val room = parts.getOrNull(4)?.toIntOrNull() ?: -1
        return StringPacket(parts[2], parts[3], room, parts.drop(5).dropLastWhile { it.isEmpty() })
    }

    fun parseJsonPacket(text: String): JsonPacket? = runCatching {
        val root = JSONObject(text)
        if (root.optString("t") != "xt") return null
        val body = root.optJSONObject("b") ?: return null
        val obj = body.optJSONObject("o") ?: return null
        JsonPacket(obj.optString("cmd"), body.optInt("r", -1), obj)
    }.getOrNull()

    fun parseLoginUserId(text: String): Int? {
        val p = text.split('%')
        return if (p.size >= 6 && p[1] == "xt" && p[2] == "loginResponse" &&
            p[4].equals("true", true)
        ) p[5].toIntOrNull() else null
    }

    fun parseJoinRoom(text: String): Int? {
        val action = Regex("action=['\"](?:joinOK|join)['\"][^>]*\\br=['\"](-?\\d+)['\"]")
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
        return action ?: Regex("\\br=['\"](-?\\d+)['\"]")
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun isLoginRejected(text: String): Boolean {
        if ("action='logKO'" in text) return true
        // Live format: `%xt%loginResponse%-1%<accepted>%...` where a stale
        // client gets `%0%` ("Your game client is an older version...").
        val p = text.split('%')
        if (p.size >= 6 && p[1] == "xt" && p[2] == "loginResponse") {
            return !p[4].equals("true", true)
        }
        return "%false%" in text
    }

    fun isServerJoinNotice(text: String): Boolean = parseServerJoinNotice(text) != null

    fun parseServerJoinNotice(text: String): String? {
        // Live format: `%xt%server%-1%You joined "<map>"%`.
        val parsed = parseStringPacket(text) ?: return null
        if (parsed.command != "server") return null
        val joined = parsed.args.firstOrNull { it.startsWith("You joined") } ?: return null
        return Regex("You joined\\s+\"([^\"]+)\"").find(joined)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
    }
}

data class StringPacket(val name: String, val command: String, val room: Int, val args: List<String>)
data class JsonPacket(val command: String, val room: Int, val payload: JSONObject)
data class ChatMessage(val channel: String, val message: String, val sender: String, val userId: Int)
