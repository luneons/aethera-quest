package com.aetheraquest.android

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

enum class WorldEntryStyle { JOIN_OK_AND_AREA, JOIN_NOTICE, JOIN_NOTICE_ALONE }
enum class TransferStyle { AREA, JOIN_NOTICE }

class ScriptedAqwSocket(
    private val failDial: Boolean = false,
    private val answerVersion: Boolean = true,
    private val blockDial: Boolean = false,
    private val holdTransfer: Boolean = false,
    private val releaseDialOnClose: Boolean = false,
    private val rejectFirstJoinAfterRoomList: Boolean = false,
    private val worldEntryStyle: WorldEntryStyle = WorldEntryStyle.JOIN_OK_AND_AREA,
    private val transferStyle: TransferStyle = TransferStyle.AREA,
    private val loginReject: Boolean = false,
    private val transferMapName: String? = null,
    private val ignoreLaterTransfers: Boolean = false,
    private val confirmEquip: Boolean = true,
) : Socket() {
    @Volatile var wasClosed = false
        private set
    @Volatile var transfers = 0
        private set
    val sent = CopyOnWriteArrayList<String>()
    val dialEntered = CountDownLatch(1)
    val releaseDial = CountDownLatch(1)
    val transferRequested = CountDownLatch(1)
    @Volatile var delayedRead: DelayedRead? = null
    private val chunks = LinkedBlockingQueue<ByteArray>()
    private val framer = PacketFramer()
    private var roomListRequested = false
    private val input = object : InputStream() {
        override fun read(): Int = error("bulk reads expected")
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            delayedRead?.let { delayed ->
                delayedRead = null
                delayed.entered.countDown()
                if (!delayed.release.await(5, TimeUnit.SECONDS)) throw IOException("test read timed out")
                val bytesFromOldSocket = delayed.value.toByteArray()
                bytesFromOldSocket.copyInto(bytes, offset)
                return bytesFromOldSocket.size
            }
            if (wasClosed) throw IOException("closed")
            val chunk = chunks.poll(100, TimeUnit.MILLISECONDS) ?: throw SocketTimeoutException()
            if (wasClosed) throw IOException("closed")
            require(chunk.size <= length)
            chunk.copyInto(bytes, offset)
            return chunk.size
        }
    }
    private val output = object : OutputStream() {
        override fun write(byte: Int) { write(byteArrayOf(byte.toByte()), 0, 1) }
        @Synchronized override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (wasClosed) throw IOException("closed")
            framer.feed(bytes.copyOfRange(offset, offset + length)).forEach { packet ->
                sent += packet
                when {
                    "action='verChk'" in packet && answerVersion -> offer("<msg t='sys'><body action='apiOK' r='0'></body></msg>")
                    "action='login'" in packet && loginReject -> offer("%xt%loginResponse%-1%0%-1%%Your game client is an older version.%")
                    "action='login'" in packet -> offer("%xt%loginResponse%-1%true%1%demo-user%Welcome%")
                    "action='getRmList'" in packet -> {
                        roomListRequested = true
                        if (!rejectFirstJoinAfterRoomList) Unit
                    }
                    "%firstJoin%" in packet && !rejectFirstJoinAfterRoomList && worldEntryStyle == WorldEntryStyle.JOIN_OK_AND_AREA -> offer("<msg t='sys'><body action='joinOK' r='42'></body></msg>", area(42))
                    "%firstJoin%" in packet && rejectFirstJoinAfterRoomList && !roomListRequested -> offer("<msg t='sys'><body action='joinOK' r='42'></body></msg>", area(42))
                    "%firstJoin%" in packet && worldEntryStyle == WorldEntryStyle.JOIN_NOTICE -> offer("%xt%server%-1%You joined \"demo-1\"%", area(42))
                    "%firstJoin%" in packet && worldEntryStyle == WorldEntryStyle.JOIN_NOTICE_ALONE -> offer("%xt%server%-1%You joined \"demo-1\"%")
                    "%retrieveInventory%" in packet -> offer("""{"t":"xt","b":{"r":42,"o":{"cmd":"loadInventory"}}}""")
                    "%equipItem%" in packet && confirmEquip -> {
                        val itemId = packet.split('%').getOrNull(5)?.toIntOrNull() ?: 0
                        offer("""{"t":"xt","b":{"r":42,"o":{"cmd":"equipItem","uid":1,"strES":"ba","ItemID":$itemId,"sType":"Cape"}}}""")
                    }
                    "%tfer%" in packet -> {
                        transferRequested.countDown()
                        transfers += 1
                        val requested = packet.split('%').getOrNull(7)
                        when {
                            holdTransfer -> Unit
                            transfers > 1 && ignoreLaterTransfers -> Unit
                            transfers > 1 && requested != null -> offer("%xt%server%-1%You joined \"$requested\"%")
                            transferStyle == TransferStyle.AREA -> offer(area(42))
                            else -> offer("%xt%server%-1%You joined \"demo-1\"%")
                        }
                    }
                }
            }
        }
    }

    /** Simulates the server dropping the connection mid-session. */
    fun fail() { wasClosed = true }

    fun offer(vararg packets: String) { chunks.offer(packets.joinToString("\u0000", postfix = "\u0000").toByteArray()) }
    override fun connect(endpoint: SocketAddress, timeout: Int) {
        dialEntered.countDown()
        if (blockDial && !releaseDial.await(5, TimeUnit.SECONDS)) throw IOException("test dial timed out")
        if (failDial || wasClosed) throw IOException("dial rejected")
    }
    override fun setTcpNoDelay(on: Boolean) = Unit
    override fun setSoTimeout(timeout: Int) = Unit
    override fun getInputStream(): InputStream = input
    override fun getOutputStream(): OutputStream = output
    override fun close() {
        wasClosed = true
        if (releaseDialOnClose) releaseDial.countDown()
    }
}

class DelayedRead(val value: String) {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
}

internal fun area(room: Int): String = """{"t":"xt","b":{"r":$room,"o":{"cmd":"moveToArea","areaId":$room,"areaName":"demo-1","uoBranch":[{"uoName":"demo-user","uid":1,"strFrame":"Enter","strPad":"Spawn"}],"mondef":[],"monmap":[],"monBranch":[]}}}"""
