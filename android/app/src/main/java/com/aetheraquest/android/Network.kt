package com.aetheraquest.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException

data class LoginToken(val username: String, val token: String, val level: Int = 1)
data class AqwServer(
    val name: String, val host: String, val port: Int, val online: Boolean,
    val full: Boolean, val upgradeOnly: Boolean,
)

object AqwApi {
    suspend fun login(username: String, password: String): LoginToken = withContext(Dispatchers.IO) {
        val connection = (java.net.URL("${AqwConfig.LOGIN_URL}?ran=${Math.random()}").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", "AQW/Aethera-Quest-Android")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val form = "user=${encode(username)}&pass=${encode(password)}&option=1"
            connection.outputStream.use { it.write(form.toByteArray(StandardCharsets.UTF_8)) }
            val source = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val raw = source?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
                ?: error("Login returned HTTP ${connection.responseCode}")
            val root = JSONObject(raw)
            val obj = root.optJSONObject("login") ?: root
            val success = obj.optInt("bSuccess", 0) == 1
            val token = obj.optString("sToken")
            if (!success || token.isBlank()) error(obj.optString("sMsg", "Authentication rejected"))
            LoginToken(obj.optString("unm", username).lowercase(), token, obj.optInt("iLevel", 1))
        } finally { connection.disconnect() }
    }

    suspend fun servers(): List<AqwServer> = withContext(Dispatchers.IO) {
        val connection = java.net.URL(AqwConfig.SERVER_LIST_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "AQW/Aethera-Quest-Android")
            val raw = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            parseServers(raw)
        } finally { connection.disconnect() }
    }

    fun parseServers(raw: String): List<AqwServer> {
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val name = obj.optString("sName").trim()
            if (name.isEmpty()) return@mapNotNull null
            val count = obj.optInt("iCount")
            val max = obj.optInt("iMax")
            val host = obj.optString("sIP").trim()
            val port = obj.optInt("iPort")
            if (!AqwConfig.isTrustedGameHost(host) || port !in 1..65535) return@mapNotNull null
            AqwServer(
                name, host, port, obj.optInt("bOnline") == 1,
                max > 0 && count >= max, obj.optInt("bUpg") == 1,
            )
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

enum class ConnectionPhase { DISCONNECTED, CONNECTING, VERSION, LOGIN, WORLD, JOINING, IN_MAP }

data class ConnectionStatus(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val message: String = "Disconnected",
    val area: AreaSnapshot = AreaSnapshot(),
    val chat: List<ChatMessage> = emptyList(),
    val afk: Boolean = false,
    val transferring: Boolean = false,
    val className: String = "",
    val skills: Map<String, SkillInfo> = emptyMap(),
)

class ConnectionSession {
    private var nextId = 0L
    private var currentId: Long? = null

    @Synchronized fun begin(): Long {
        val id = ++nextId
        currentId = id
        return id
    }

    @Synchronized fun isCurrent(id: Long): Boolean = currentId == id
    @Synchronized fun current(): Long? = currentId
    @Synchronized fun invalidate() { currentId = null }
}

/** Keeps packets that arrived in the same socket read after a handshake match. */
class PacketInbox {
    private val pending = ArrayDeque<String>()

    @Synchronized fun offer(packets: List<String>) { pending.addAll(packets) }
    @Synchronized fun poll(): String? = if (pending.isEmpty()) null else pending.removeFirst()
    @Synchronized fun clear() { pending.clear() }
}

class GameConnection(private val socketFactory: () -> Socket = { Socket() }) : Closeable {
    private val statusFlow = MutableStateFlow(ConnectionStatus())
    /** Exposes the live skill table from the most recent `sAct` packet. */
    val skillFlow = MutableStateFlow<Map<String, SkillInfo>>(emptyMap())
    val status = statusFlow.asStateFlow()
    private val stateLock = Any()
    private val sessions = ConnectionSession()
    private var socket: Socket? = null
    private var dialingSocket: Socket? = null
    private var reducer: AreaReducer? = null
    private var roomId = 1
    private var selfUsername = ""
    private var combatSequence = 0
    private val packetInbox = PacketInbox()
    private val framer = PacketFramer()
    /** Active in-flight transfer requested by [transfer]; arrival is published from [reduce]. */
    private var transferMap: String? = null
    private var transferResult: CompletableDeferred<AreaSnapshot>? = null

    suspend fun connect(login: LoginToken, server: AqwServer, map: String) = withContext(Dispatchers.IO) {
        val session = synchronized(stateLock) {
            close()
            reducer = AreaReducer(login.username)
            selfUsername = login.username
            roomId = 1
            combatSequence = 0
            sessions.begin()
        }
        var allocated: Socket? = null
        var cancellationCloser: Job? = null
        try {
            require(AqwConfig.isTrustedGameHost(server.host)) { "Untrusted game server host" }
            require(server.port in 1..65535) { "Invalid game server port" }
            update(session, ConnectionPhase.CONNECTING, "Connecting to ${server.name}…")
            val connected = socketFactory()
            allocated = connected
            cancellationCloser = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    runCatching { connected.close() }
                }
            }
            synchronized(stateLock) {
                ensureCurrent(session)
                dialingSocket = connected
            }
            try {
                connected.tcpNoDelay = true
                connected.connect(InetSocketAddress(server.host, server.port), 20_000)
                connected.soTimeout = 500
            } catch (error: Throwable) {
                runCatching { connected.close() }
                throw error
            } finally {
                synchronized(stateLock) {
                    if (dialingSocket === connected) dialingSocket = null
                }
            }
            synchronized(stateLock) {
                ensureCurrent(session)
                socket = connected
            }
            update(session, ConnectionPhase.VERSION, "Checking SmartFox version…")
            send(session, SfsProtocol.verCheck())
            waitFor(session, 20_000) { "action='apiOK'" in it || "action='verChk'" in it || "<ver " in it }

            update(session, ConnectionPhase.LOGIN, "Logging into game server…")
            send(session, SfsProtocol.login(login.username, login.token))
            val loginReply = waitFor(session, 20_000) {
                "loginResponse" in it || "action='logOK'" in it || "action='logKO'" in it
            }
            if (SfsProtocol.isLoginRejected(loginReply)) error("Game server rejected login")
            val userId = SfsProtocol.parseLoginUserId(loginReply)
            synchronized(stateLock) {
                ensureCurrent(session)
                reducer?.setSelfUserId(userId)
            }

            update(session, ConnectionPhase.WORLD, "Entering world…")
            // The game server accepts firstJoin as the world-entry trigger. Sending
            // getRmList first can leave the session waiting forever after login;
            // request the optional room list only after the world handshake starts.
            // Live servers acknowledge entry with `%xt%server%You joined "<map>"%`
            // and may never send sys joinOK, so accept either proof of entry.
            send(session, SfsProtocol.firstJoin())
            send(session, SfsProtocol.getRoomList())
            val joinReply = waitFor(session, 20_000) {
                "action='joinOK'" in it || SfsProtocol.isServerJoinNotice(it)
            }
            val joinedMap = SfsProtocol.parseServerJoinNotice(joinReply)
            synchronized(stateLock) {
                ensureCurrent(session)
                SfsProtocol.parseJoinRoom(joinReply)?.let { roomId = it }
            }
            // A `You joined` notice is itself proof of entry: the server may send
            // no further area packet, so waiting for another generation here only
            // burns the whole phase timeout before failing.
            if (joinedMap != null) {
                publishMapArrival(session, joinedMap)
                reducer?.snapshot()
            } else waitForArea(session, 20_000)

            if (userId != null) {
                send(session, SfsProtocol.retrieveInventory(roomId, userId))
                runCatching { waitFor(session, 20_000) { "loadInventory" in it } }
            }

            update(session, ConnectionPhase.JOINING, "Joining $map…")
            val deadline = System.currentTimeMillis() + 20_000
            var joined: AreaSnapshot? = null
            while (System.currentTimeMillis() < deadline && joined == null) {
                send(session, SfsProtocol.join(selfUsername, map))
                // Live servers acknowledge tfer with `%xt%server%You joined "<map>"%`
                // and may never send moveToArea, so accept either proof of transfer.
                joined = runCatching { waitForMapJoin(session, 3_000) }.getOrNull()
                if (joined == null) delay(800)
            }
            if (joined == null) error("Timed out joining $map")
            update(session, ConnectionPhase.IN_MAP, "Connected to ${joined.mapName ?: map}")
            receiveLoop(session)
        } catch (error: Throwable) {
            if (!closeOwned(session, error.message ?: "Connection failed")) return@withContext
            currentCoroutineContext().ensureActive()
            throw error
        } finally {
            cancellationCloser?.cancel()
            runCatching { allocated?.close() }
        }
    }

    suspend fun move(x: Int, y: Int, speed: Int = 10) = withContext(Dispatchers.IO) {
        require(status.value.phase == ConnectionPhase.IN_MAP) { "Not in a map" }
        val session = currentSession()
        send(session, SfsProtocol.move(roomId, x, y, speed))
        synchronized(stateLock) {
            ensureCurrent(session)
            reducer?.noteLocalPosition(x, y)
            reducer?.snapshot()?.let(::publish)
        }
    }

    suspend fun moveToCell(cell: String, pad: String) = withContext(Dispatchers.IO) {
        require(cell.isNotBlank()) { "Cell is required" }
        require(status.value.phase == ConnectionPhase.IN_MAP) { "Not in a map" }
        val session = currentSession()
        send(session, SfsProtocol.moveToCell(roomId, cell, pad))
        synchronized(stateLock) {
            ensureCurrent(session)
            reducer?.noteLocalCell(cell, pad)
            reducer?.snapshot()?.let(::publish)
        }
    }

    /**
     * Transfers to a different map on the already-open socket. This never
     * drops the connection: a late area packet from the previous map would
     * otherwise look like a successful arrival and drop the user into the
     * wrong room.
     */
    suspend fun transfer(map: String, timeoutMs: Long = 20_000): Unit = withContext(Dispatchers.IO) {
        require(map.isNotBlank()) { "Map name is required" }
        require(map.none { it.isWhitespace() || it == '%' || it == '\u0000' }) { "Invalid map name" }
        require(status.value.phase == ConnectionPhase.IN_MAP) { "Not in a map" }
        val session = currentSession()
        val arrival = CompletableDeferred<AreaSnapshot>()
        synchronized(stateLock) {
            ensureCurrent(session)
            // The receive loop owns packet reading, so arrival is reported from
            // there instead of racing it for the join notice.
            transferMap = map
            transferResult = arrival
            statusFlow.value = statusFlow.value.copy(transferring = true, message = "Joining $map…")
        }
        try {
            val deadline = System.currentTimeMillis() + timeoutMs
            var joined: AreaSnapshot? = null
            while (joined == null && System.currentTimeMillis() < deadline) {
                currentCoroutineContext().ensureActive()
                send(session, SfsProtocol.join(selfUsername, map))
                joined = withTimeoutOrNull(3_000) { arrival.await() }
            }
            if (joined == null) error("Timed out joining $map")
            update(session, ConnectionPhase.IN_MAP, "Connected to ${joined.mapName ?: map}")
        } finally {
            synchronized(stateLock) {
                if (transferResult === arrival) {
                    transferResult = null
                    transferMap = null
                    statusFlow.value = statusFlow.value.copy(transferring = false)
                }
            }
        }
    }

    suspend fun attack(mapId: Int, action: String = "aa") = withContext(Dispatchers.IO) {
        require(status.value.phase == ConnectionPhase.IN_MAP) { "Not in a map" }
        require(mapId > 0) { "Pick a monster first" }
        val session = currentSession()
        val sequence = synchronized(stateLock) {
            ensureCurrent(session)
            combatSequence.also { combatSequence = (combatSequence + 1) % 31 }
        }
        send(session, SfsProtocol.attack(roomId, sequence, action, "m:$mapId"))
    }

    /**
     * Sends one combat action. `action` is a skill ref from the live `sAct`
     * payload (`aa`, `a1`..`a4`), never a guess.
     */
    suspend fun cast(mapId: Int, action: String) = withContext(Dispatchers.IO) {
        require(status.value.phase == ConnectionPhase.IN_MAP) { "Not in a map" }
        require(mapId > 0) { "Pick a monster first" }
        require(action in setOf("aa", "a1", "a2", "a3", "a4")) { "Unsupported action: $action" }
        val session = currentSession()
        val sequence = synchronized(stateLock) {
            ensureCurrent(session)
            combatSequence.also { combatSequence = (combatSequence + 1) % 31 }
        }
        send(session, SfsProtocol.attack(roomId, sequence, action, "m:$mapId"))
    }

    suspend fun sendChat(message: String, channel: String = "zone") = withContext(Dispatchers.IO) {
        require(message.isNotBlank()) { "Message is required" }
        require(status.value.phase == ConnectionPhase.IN_MAP) { "Not in a map" }
        val session = currentSession()
        send(session, SfsProtocol.chat(roomId, message, channel))
        // The server echoes every accepted message back as a chatm packet, so the
        // log stays server-authoritative instead of double-adding our own line.
    }

    suspend fun setAfk(enabled: Boolean) = withContext(Dispatchers.IO) {
        require(status.value.phase == ConnectionPhase.IN_MAP) { "Not in a map" }
        val session = currentSession()
        send(session, SfsProtocol.afk(enabled))
        synchronized(stateLock) {
            ensureCurrent(session)
            statusFlow.value = statusFlow.value.copy(afk = enabled)
        }
    }

    private suspend fun receiveLoop(session: Long) {
        var lastKeepAlive = System.currentTimeMillis()
        while (sessions.isCurrent(session)) {
            currentCoroutineContext().ensureActive()
            nextPacket(session)?.let { reduce(session, decodeSmartFoxPacket(it)) }
            if (System.currentTimeMillis() - lastKeepAlive >= 25_000) {
                send(session, SfsProtocol.roundTrip(roomId))
                lastKeepAlive = System.currentTimeMillis()
            }
            yield()
        }
    }

    private fun reduce(session: Long, packet: String) {
        synchronized(stateLock) {
            ensureCurrent(session)
            val chat = SfsProtocol.parseChat(packet)
            if (chat != null) {
                statusFlow.value = statusFlow.value.copy(
                    chat = (statusFlow.value.chat + chat).takeLast(200),
                )
            }
            // A pending transfer is completed by the live receive loop, which is
            // the only reader of the socket once connect() returns.
            val pending = transferResult
            val wanted = transferMap
            if (pending != null && wanted != null) {
                val notice = SfsProtocol.parseServerJoinNotice(packet)?.trim()?.ifEmpty { null }
                if (notice != null) {
                    val arrived = completeTransfer(session, notice)
                    if (arrived != null) pending.complete(arrived)
                    return
                }
            }
            if (reducer?.feed(packet) == true) {
                val area = reducer!!.snapshot()
                area.roomId?.let { roomId = it }
                publish(area)
                if (pending != null && wanted != null && area.mapName != null &&
                    area.mapName.equals(wanted, ignoreCase = true)
                ) {
                    transferResult = null
                    transferMap = null
                    pending.complete(area)
                }
            }
            // Combat state is independent of the area snapshot: the skill table
            // and the equipped class arrive on their own packets, so they are
            // read even when the area reducer reports no change.
            SfsProtocol.parseJsonPacket(packet)?.let { parsed ->
                when (parsed.command) {
                    "sAct" -> {
                        val table = parseSkillTable(parsed.payload)
                        if (table.isNotEmpty()) {
                            skillFlow.value = table
                            statusFlow.value = statusFlow.value.copy(skills = table)
                        }
                    }
                    "updateClass", "loadInventoryBig" -> parseClassName(parsed.payload)?.let { name ->
                        statusFlow.value = statusFlow.value.copy(className = name)
                    }
                }
            }
        }
    }

    /** Resets area state for the new room and returns the arrival snapshot. */
    private fun completeTransfer(session: Long, mapName: String): AreaSnapshot? {
        ensureCurrent(session)
        val base = reducer?.snapshot() ?: AreaSnapshot()
        val next = base.copy(generation = base.generation + 1, mapName = mapName, roomId = base.roomId)
        reducer?.setMapState(next)
        publish(next)
        transferResult = null
        transferMap = null
        statusFlow.value = statusFlow.value.copy(transferring = false)
        return next
    }

    private fun publish(area: AreaSnapshot) {
        statusFlow.value = statusFlow.value.copy(area = area)
    }

    private fun update(session: Long, phase: ConnectionPhase, message: String) {
        synchronized(stateLock) {
            if (sessions.isCurrent(session)) {
                statusFlow.value = statusFlow.value.copy(phase = phase, message = message)
            }
        }
    }

    private fun send(session: Long, bytes: ByteArray) {
        val output = synchronized(stateLock) {
            ensureCurrent(session)
            socket?.getOutputStream() ?: error("Socket is closed")
        }
        output.write(bytes)
        output.flush()
    }

    private suspend fun waitFor(session: Long, timeoutMs: Long, predicate: (String) -> Boolean): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            ensureCurrent(session)
            val packet = nextPacket(session) ?: continue
            reduce(session, decodeSmartFoxPacket(packet))
            if (predicate(packet)) return packet
        }
        error("Timed out waiting for server")
    }

    private suspend fun waitForArea(session: Long, timeoutMs: Long): AreaSnapshot? {
        val before = reducer?.snapshot()?.generation ?: 0
        waitFor(session, timeoutMs) {
            val snapshot = reducer?.snapshot()
            snapshot != null && snapshot.generation > before
        }
        return reducer?.snapshot()
    }

    private suspend fun waitForMapJoin(session: Long, timeoutMs: Long): AreaSnapshot? {
        // Accept either proof of arrival: a fresh moveToArea state generation, or
        // the live server's `%xt%server%You joined "<map>"%` notice. Extract the
        // map name from the notice so late area packets from a previous map do
        // not satisfy the wait for the new destination.
        val before = reducer?.snapshot()?.generation ?: 0
        waitFor(session, timeoutMs) { packet ->
            val joined = SfsProtocol.parseServerJoinNotice(packet)?.trim()?.ifEmpty { null }
            if (joined != null) {
                publishMapArrival(session, joined)
                true
            } else {
                val snapshot = reducer?.snapshot()
                snapshot != null && snapshot.generation > before
            }
        }
        return reducer?.snapshot()
    }

    private fun publishMapArrival(session: Long, mapName: String) {
        synchronized(stateLock) {
            ensureCurrent(session)
            val base = reducer?.snapshot() ?: AreaSnapshot()
            val next = base.copy(generation = base.generation + 1, mapName = mapName)
            reducer?.setMapState(next)
            publish(next)
        }
    }

    private fun readPackets(session: Long): List<String> {
        val input = synchronized(stateLock) {
            ensureCurrent(session)
            socket?.getInputStream() ?: error("Socket is closed")
        }
        val bytes = ByteArray(4096)
        return try {
            val count = input.read(bytes)
            if (count < 0) error("Server closed connection")
            synchronized(stateLock) {
                ensureCurrent(session)
                framer.feed(bytes.copyOf(count))
            }
        } catch (_: java.net.SocketTimeoutException) { emptyList() }
    }

    private fun nextPacket(session: Long): String? {
        synchronized(stateLock) {
            ensureCurrent(session)
            packetInbox.poll()?.let { return it }
        }
        val packets = readPackets(session)
        synchronized(stateLock) {
            ensureCurrent(session)
            packetInbox.offer(packets)
            return packetInbox.poll()
        }
    }

    private fun currentSession(): Long = sessions.current() ?: error("Connection is closed")

    private fun ensureCurrent(session: Long) {
        if (!sessions.isCurrent(session)) throw CancellationException("Connection superseded")
    }

    override fun close() {
        val sockets = synchronized(stateLock) {
            sessions.invalidate()
            transferResult?.cancel()
            transferResult = null
            transferMap = null
            val old = listOfNotNull(socket, dialingSocket)
            socket = null
            dialingSocket = null
            packetInbox.clear()
            framer.reset()
            statusFlow.value = ConnectionStatus()
            old
        }
        sockets.forEach { old -> runCatching { old.close() } }
    }

    private fun closeOwned(session: Long, message: String): Boolean {
        val sockets = synchronized(stateLock) {
            if (!sessions.isCurrent(session)) return false
            sessions.invalidate()
            transferResult?.cancel()
            transferResult = null
            transferMap = null
            val old = listOfNotNull(socket, dialingSocket)
            socket = null
            dialingSocket = null
            packetInbox.clear()
            framer.reset()
            statusFlow.value = statusFlow.value.copy(phase = ConnectionPhase.DISCONNECTED, message = message)
            old
        }
        sockets.forEach { old -> runCatching { old.close() } }
        return true
    }
}
