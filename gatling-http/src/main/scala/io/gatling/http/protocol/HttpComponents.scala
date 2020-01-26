/*
 * Copyright 2011-2020 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.http.protocol

import io.gatling.core.CoreComponents
import io.gatling.core.protocol.ProtocolComponents
import io.gatling.core.session.Session
import io.gatling.http.cache.HttpCaches
import io.gatling.http.engine.HttpEngine
import io.gatling.http.engine.tx.HttpTxExecutor

final case class HttpComponents(
    coreComponents: CoreComponents,
    httpProtocol: HttpProtocol,
    httpEngine: HttpEngine,
    httpCaches: HttpCaches,
    httpTxExecutor: HttpTxExecutor
) extends ProtocolComponents {

  override lazy val onStart: Session => Session =
    (httpCaches.setSslContexts(httpProtocol, httpEngine)
      andThen httpCaches.setNameResolver(httpProtocol, httpEngine)
      andThen httpCaches.setLocalAddress(httpProtocol)
      andThen httpCaches.setBaseUrl(httpProtocol)
      andThen httpCaches.setWsBaseUrl(httpProtocol)
      andThen httpCaches.setHttp2PriorKnowledge(httpProtocol))

  override lazy val onExit: Session => Unit =
    session => {
      httpCaches.nameResolver(session).foreach(_.close())
      httpCaches.sslContexts(session).foreach(_.close())
      httpEngine.flushClientIdChannels(session.userId)
    }
}
