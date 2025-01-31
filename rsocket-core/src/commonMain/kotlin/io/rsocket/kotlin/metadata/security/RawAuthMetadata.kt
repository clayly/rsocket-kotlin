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

package io.rsocket.kotlin.metadata.security

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.io.*

@ExperimentalMetadataApi
public class RawAuthMetadata(
    public override val type: AuthType,
    public val content: ByteReadPacket,
) : AuthMetadata {

    override fun BytePacketBuilder.writeContent() {
        writePacket(content)
    }

    public companion object Reader : AuthMetadataReader<RawAuthMetadata> {
        override fun ByteReadPacket.readContent(type: AuthType, pool: ObjectPool<ChunkBuffer>): RawAuthMetadata {
            val content = readPacket(pool)
            return RawAuthMetadata(type, content)
        }
    }
}

@ExperimentalMetadataApi
public fun RawAuthMetadata.hasAuthTypeOf(reader: AuthMetadataReader<*>): Boolean = type == reader.mimeType

@ExperimentalMetadataApi
public fun <AM : AuthMetadata> RawAuthMetadata.read(reader: AuthMetadataReader<AM>, pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool): AM {
    return readOrNull(reader, pool) ?: run {
        content.release()
        error("Expected auth type '${reader.mimeType}' but was '$mimeType'")
    }
}

@ExperimentalMetadataApi

public fun <AM : AuthMetadata> RawAuthMetadata.readOrNull(
    reader: AuthMetadataReader<AM>,
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
): AM? {
    if (type != reader.mimeType) return null

    with(reader) {
        return content.use { it.readContent(type, pool) }
    }
}
