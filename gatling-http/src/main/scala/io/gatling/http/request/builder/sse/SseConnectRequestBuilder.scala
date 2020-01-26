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

import io.gatling.core.session._
import io.gatling.http.{ HeaderNames, HeaderValues }
import io.gatling.http.action.sse.SseConnectBuilder
import io.gatling.http.client.Request
import io.gatling.http.protocol.HttpComponents
import io.gatling.http.request.builder.{ CommonAttributes, RequestBuilder }

import io.netty.handler.codec.http.HttpMethod

object SseConnectRequestBuilder {

  private val SseHeaderValueExpression = HeaderValues.TextEventStream.expressionSuccess
  private val CacheControlNoCacheValueExpression = HeaderValues.NoCache.expressionSuccess

  def apply(requestName: Expression[String], url: Expression[String], sseName: String): SseConnectRequestBuilder =
    new SseConnectRequestBuilder(CommonAttributes(requestName, HttpMethod.GET, Left(url)), sseName)
      .header(HeaderNames.Accept, SseHeaderValueExpression)
      .header(HeaderNames.CacheControl, CacheControlNoCacheValueExpression)

  implicit def toActionBuilder(requestBuilder: SseConnectRequestBuilder): SseConnectBuilder =
    SseConnectBuilder(requestBuilder.commonAttributes.requestName, requestBuilder.sseName, requestBuilder, Nil)
}

final case class SseConnectRequestBuilder(
    commonAttributes: CommonAttributes,
    sseName: String
) extends RequestBuilder[SseConnectRequestBuilder] {

  override private[http] def newInstance(commonAttributes: CommonAttributes) = new SseConnectRequestBuilder(commonAttributes, sseName)

  def build(httpComponents: HttpComponents): Expression[Request] =
    new SseRequestExpressionBuilder(commonAttributes, httpComponents.httpCaches, httpComponents.httpProtocol, httpComponents.coreComponents.configuration).build
}
