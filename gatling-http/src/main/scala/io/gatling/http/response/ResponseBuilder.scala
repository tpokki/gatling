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

package io.gatling.http.response

import java.nio.charset.Charset
import java.security.MessageDigest

import scala.collection.breakOut
import scala.math.max
import scala.util.control.NonFatal

import io.gatling.commons.util.Clock
import io.gatling.commons.util.Collections._
import io.gatling.commons.util.Maps._
import io.gatling.commons.util.StringHelper.bytes2Hex
import io.gatling.commons.util.Throwables._
import io.gatling.core.check.ChecksumCheck
import io.gatling.core.config.GatlingConfiguration
import io.gatling.http.HeaderNames
import io.gatling.http.check.HttpCheckScope.Body
import io.gatling.http.client.Request
import io.gatling.http.engine.response._
import io.gatling.http.util.HttpHelper.{ extractCharsetFromContentType, isCss, isHtml }
import io.gatling.http.request.HttpRequestConfig

import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.{ EmptyHttpHeaders, HttpHeaders, HttpResponseStatus }

object ResponseBuilder extends StrictLogging {

  def newResponseBuilderFactory(
      requestConfig: HttpRequestConfig,
      clock: Clock,
      configuration: GatlingConfiguration
  ): ResponseBuilderFactory = {

    val digests: Map[String, MessageDigest] =
      requestConfig.checks
        .map(_.wrapped)
        .collect { case check: ChecksumCheck[_] => check.algorithm -> MessageDigest.getInstance(check.algorithm) }(breakOut)

    val storeBodyParts = IsHttpDebugEnabled ||
      // we can't assume anything about if and how the response body will be used,
      // let's force bytes so we don't risk decoding binary content
      requestConfig.responseTransformer.isDefined ||
      requestConfig.checks.exists(_.scope == Body)

    request =>
      new ResponseBuilder(
        request,
        digests,
        storeBodyParts,
        requestConfig.httpProtocol.responsePart.inferHtmlResources,
        configuration.core.charset,
        clock
      )
  }
}

class ResponseBuilder(
    request: Request,
    digests: Map[String, MessageDigest],
    storeBodyParts: Boolean,
    inferHtmlResources: Boolean,
    defaultCharset: Charset,
    clock: Clock
) {

  var storeHtmlOrCss: Boolean = _
  var startTimestamp: Long = _
  var endTimestamp: Long = _
  private var isHttp2: Boolean = _
  private var status: Option[HttpResponseStatus] = None
  private var wireRequestHeaders: HttpHeaders = EmptyHttpHeaders.INSTANCE

  private var headers: HttpHeaders = EmptyHttpHeaders.INSTANCE
  private var chunks: List[ByteBuf] = Nil

  def updateStartTimestamp(): Unit =
    startTimestamp = clock.nowMillis

  def updateEndTimestamp(): Unit =
    endTimestamp = clock.nowMillis

  def accumulate(wireRequestHeaders: HttpHeaders): Unit =
    this.wireRequestHeaders = wireRequestHeaders

  def accumulate(status: HttpResponseStatus, headers: HttpHeaders): Unit = {
    updateEndTimestamp()

    this.status = Some(status)
    if (this.headers eq EmptyHttpHeaders.INSTANCE) {
      this.headers = headers
      storeHtmlOrCss = inferHtmlResources && (isHtml(headers) || isCss(headers))
    } else {
      // trailing headers, wouldn't contain ContentType
      this.headers.add(headers)
    }
  }

  def setHttp2(isHttp2: Boolean): Unit = this.isHttp2 = isHttp2

  def accumulate(byteBuf: ByteBuf): Unit = {
    updateEndTimestamp()

    if (byteBuf.isReadable) {
      if (storeBodyParts || storeHtmlOrCss) {
        chunks = byteBuf.retain() :: chunks // beware, we have to retain!
      }

      if (digests.nonEmpty)
        for {
          nioBuffer <- byteBuf.nioBuffers
          digest <- digests.values
        } digest.update(nioBuffer.duplicate)
    }
  }

  private def resolveCharset: Charset =
    Option(headers.get(HeaderNames.ContentType))
      .flatMap(extractCharsetFromContentType)
      .getOrElse(defaultCharset)

  def buildResponse: HttpResult =
    status match {
      case Some(s) =>
        try {
          // Clock source might not be monotonic.
          // Moreover, ProgressListener might be called AFTER ChannelHandler methods
          // ensure response doesn't end before starting
          endTimestamp = max(endTimestamp, startTimestamp)

          val checksums = digests.forceMapValues(md => bytes2Hex(md.digest))

          val contentLength = chunks.sumBy(_.readableBytes)

          val resolvedCharset = resolveCharset

          val chunksOrderedByArrival = chunks.reverse
          val body: ResponseBody = ResponseBody(chunksOrderedByArrival, resolvedCharset)

          Response(request, wireRequestHeaders, s, headers, body, checksums, contentLength, resolvedCharset, startTimestamp, endTimestamp, isHttp2)
        } catch {
          case NonFatal(t) => buildFailure(t)
        }
      case _ => buildFailure("How come we're trying to build a response with no status?!")
    }

  def buildFailure(t: Throwable): HttpFailure = buildFailure(t.detailedMessage)

  private def buildFailure(errorMessage: String): HttpFailure =
    HttpFailure(request, wireRequestHeaders, startTimestamp, endTimestamp, errorMessage)

  def releaseChunks(): Unit = {
    chunks.foreach(_.release())
    chunks = Nil
  }
}
