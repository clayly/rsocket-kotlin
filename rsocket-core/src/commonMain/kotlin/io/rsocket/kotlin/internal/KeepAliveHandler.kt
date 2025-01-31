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

package io.rsocket.kotlin.internal

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.keepalive.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*

internal class KeepAliveHandler(
    private val keepAlive: KeepAlive,
    private val offerFrame: (frame: Frame) -> Unit,
) {

    private val lastMark = atomic(currentMillis())

    fun receive(frame: KeepAliveFrame) {
        lastMark.value = currentMillis()
        if (frame.respond) {
            offerFrame(KeepAliveFrame(false, 0, frame.data))
        }
    }

    fun startIn(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                delay(keepAlive.intervalMillis.toLong())
                if (currentMillis() - lastMark.value >= keepAlive.maxLifetimeMillis) {
                    //for K/N
                    scope.cancel("Keep alive failed", RSocketError.ConnectionError("No keep-alive for ${keepAlive.maxLifetimeMillis} ms"))
                    break
                }
                offerFrame(KeepAliveFrame(true, 0, ByteReadPacket.Empty))
            }
        }
    }
}

internal expect fun currentMillis(): Long
