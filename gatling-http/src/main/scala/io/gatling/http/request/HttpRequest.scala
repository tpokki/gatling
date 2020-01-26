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

package io.gatling.http.request

import io.gatling.commons.validation.Validation
import io.gatling.core.session._
import io.gatling.http.ResponseTransformer
import io.gatling.http.check.HttpCheck
import io.gatling.http.client.Request
import io.gatling.http.protocol.HttpProtocol

final case class HttpRequestConfig(
    checks: List[HttpCheck],
    responseTransformer: Option[ResponseTransformer],
    maxRedirects: Int,
    throttled: Boolean,
    silent: Option[Boolean],
    followRedirect: Boolean,
    explicitResources: List[HttpRequestDef],
    httpProtocol: HttpProtocol
)

final case class HttpRequestDef(
    requestName: Expression[String],
    clientRequest: Expression[Request],
    requestConfig: HttpRequestConfig
) {

  def build(requestName: String, session: Session): Validation[HttpRequest] =
    clientRequest(session).map(HttpRequest(requestName, _, requestConfig))
}

final case class HttpRequest(requestName: String, clientRequest: Request, requestConfig: HttpRequestConfig) {

  def isSilent(root: Boolean): Boolean = {

    val requestPart = requestConfig.httpProtocol.requestPart

    def silentBecauseProtocolSilentURI: Boolean = requestPart.silentUri match {
      case Some(silentUri) => silentUri.matcher(clientRequest.getUri.toUrl).matches
      case _               => false
    }

    def silentBecauseProtocolSilentResources = !root && requestPart.silentResources

    requestConfig.silent match {
      case Some(silent) => silent
      case _            => silentBecauseProtocolSilentURI || silentBecauseProtocolSilentResources
    }
  }
}
