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

package io.gatling.http.request.builder.sse

import io.gatling.commons.validation.Validation
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.Session
import io.gatling.http.cache.HttpCaches
import io.gatling.http.protocol.HttpProtocol
import io.gatling.http.request.builder.{ CommonAttributes, RequestExpressionBuilder }
import io.gatling.http.client.{ RequestBuilder => ClientRequestBuilder }

class SseRequestExpressionBuilder(
    commonAttributes: CommonAttributes,
    httpCaches: HttpCaches,
    httpProtocol: HttpProtocol,
    configuration: GatlingConfiguration
) extends RequestExpressionBuilder(commonAttributes, httpCaches, httpProtocol, configuration) {

  override protected def configureRequestBuilder(session: Session, requestBuilder: ClientRequestBuilder): Validation[ClientRequestBuilder] =
    // disable request timeout for SSE
    super.configureRequestBuilder(session, requestBuilder.setRequestTimeout(-1))
}
