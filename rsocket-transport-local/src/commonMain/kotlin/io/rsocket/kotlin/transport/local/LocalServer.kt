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

@file:OptIn(TransportApi::class)

package io.rsocket.kotlin.transport.local

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

public class LocalServer(
    parentJob: Job? = null,
    private val pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool,
) : ServerTransport<Job>, ClientTransport {
    public val job: Job = SupervisorJob(parentJob)
    private val connections = Channel<Connection>()

    override suspend fun connect(): Connection {
        @Suppress("INVISIBLE_MEMBER")
        val clientChannel = SafeChannel<ByteReadPacket>(Channel.UNLIMITED)

        @Suppress("INVISIBLE_MEMBER")
        val serverChannel = SafeChannel<ByteReadPacket>(Channel.UNLIMITED)
        val connectionJob = Job(job)
        connectionJob.invokeOnCompletion {
            val error = CancellationException("Connection failed", it)
            clientChannel.cancel(error)
            serverChannel.cancel(error)
        }
        val clientConnection = LocalConnection(serverChannel, clientChannel, pool, connectionJob)
        val serverConnection = LocalConnection(clientChannel, serverChannel, pool, connectionJob)
        connections.send(serverConnection)
        return clientConnection
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun start(accept: suspend (Connection) -> Unit): Job =
        GlobalScope.launch(job + Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            supervisorScope {
                connections.consumeEach { connection ->
                    launch(start = CoroutineStart.UNDISPATCHED) { accept(connection) }
                }
            }
        }
}
