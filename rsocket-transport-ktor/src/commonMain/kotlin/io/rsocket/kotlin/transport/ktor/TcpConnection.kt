/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin.transport.ktor

import io.ktor.network.sockets.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.Connection
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.*
import kotlin.native.concurrent.*

@SharedImmutable
internal val ignoreExceptionHandler = CoroutineExceptionHandler { _, _ -> }

@OptIn(TransportApi::class)
internal class TcpConnection(private val socket: Socket) : Connection, CoroutineScope {
    override val job: Job = socket.socketContext
    override val coroutineContext: CoroutineContext = job + Dispatchers.Unconfined + ignoreExceptionHandler

    @Suppress("INVISIBLE_MEMBER")
    private val sendChannel = SafeChannel<ByteReadPacket>(8)

    @Suppress("INVISIBLE_MEMBER")
    private val receiveChannel = SafeChannel<ByteReadPacket>(8)

    init {
        launch {
            socket.openWriteChannel(autoFlush = true).use {
                while (isActive) {
                    val packet = sendChannel.receive()
                    val length = packet.remaining.toInt()
                    try {
                        writePacket {
                            @Suppress("INVISIBLE_MEMBER")
                            writeLength(length)
                            writePacket(packet)
                        }
                    } catch (e: Throwable) {
                        packet.close()
                        throw e
                    }
                }
            }
        }
        launch {
            socket.openReadChannel().apply {
                while (isActive) {
                    @Suppress("INVISIBLE_MEMBER")
                    val length = readPacket(3).readLength()
                    val packet = readPacket(length)
                    try {
                        receiveChannel.send(packet)
                    } catch (cause: Throwable) {
                        packet.close()
                        throw cause
                    }
                }
            }
        }
        job.invokeOnCompletion { cause ->
            val error = cause?.let { it as? CancellationException ?: CancellationException("Connection failed", it) }
            sendChannel.cancel(error)
            receiveChannel.cancel(error)
        }
    }

    override suspend fun send(packet: ByteReadPacket): Unit = sendChannel.send(packet)

    override suspend fun receive(): ByteReadPacket = receiveChannel.receive()
}
