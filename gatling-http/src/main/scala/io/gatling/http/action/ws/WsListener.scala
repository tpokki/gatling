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

package io.gatling.http.action.ws

import io.gatling.commons.util.Clock
import io.gatling.core.stats.StatsEngine
import io.gatling.http.action.ws.fsm._
import io.gatling.http.client.WebSocketListener
import io.gatling.http.util.HttpHelper
import io.gatling.netty.util.{ ByteBufUtils, Utf8ByteBufCharsetDecoder }

import akka.actor.ActorRef
import com.typesafe.scalalogging.LazyLogging
import io.netty.handler.codec.http.cookie.Cookie
import io.netty.handler.codec.http.websocketx.{ BinaryWebSocketFrame, CloseWebSocketFrame, PongWebSocketFrame, TextWebSocketFrame }
import io.netty.handler.codec.http.{ HttpHeaders, HttpResponseStatus }

class WsListener(wsActor: ActorRef, statsEngine: StatsEngine, clock: Clock) extends WebSocketListener with LazyLogging {

  private var cookies: List[Cookie] = Nil

  //[fl]
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //
  //[fl]

  override def onHttpResponse(httpResponseStatus: HttpResponseStatus, httpHeaders: HttpHeaders): Unit = {
    logger.debug(s"Received response to WebSocket CONNECT: $httpResponseStatus $httpHeaders")
    cookies = HttpHelper.responseCookies(httpHeaders)
  }

  override def onWebSocketOpen(): Unit =
    wsActor ! WebSocketConnected(this, cookies, clock.nowMillis)

  override def onCloseFrame(frame: CloseWebSocketFrame): Unit =
    wsActor ! WebSocketClosed(frame.statusCode, frame.reasonText, clock.nowMillis)

  override def onTextFrame(frame: TextWebSocketFrame): Unit =
    wsActor ! TextFrameReceived(Utf8ByteBufCharsetDecoder.decodeUtf8(frame.content()), clock.nowMillis)

  override def onBinaryFrame(frame: BinaryWebSocketFrame): Unit =
    wsActor ! BinaryFrameReceived(ByteBufUtils.byteBuf2Bytes(frame.content()), clock.nowMillis)

  override def onPongFrame(frame: PongWebSocketFrame): Unit =
    logger.debug("Received PONG frame")

  override def onThrowable(t: Throwable): Unit =
    wsActor ! WebSocketCrashed(t, clock.nowMillis)
}
