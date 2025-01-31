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

package io.rsocket.kotlin.logging

import io.rsocket.kotlin.*

/**
 * Logger implementation, that never print
 */
@RSocketLoggingApi
public object NoopLogger : Logger, LoggerFactory {
    override val tag: String get() = "noop"
    override fun isLoggable(level: LoggingLevel): Boolean = false
    override fun rawLog(level: LoggingLevel, throwable: Throwable?, message: Any?): Unit = Unit
    override fun logger(tag: String): Logger = this
}
