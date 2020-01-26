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

package io.gatling.http.action.sse

import java.nio.charset.StandardCharsets.UTF_8

import io.gatling.BaseSpec
import io.gatling.http.action.sse.fsm.{ ServerSentEvent, SseStreamDecoder }

import io.netty.buffer.Unpooled

class SseStreamDecoderSpec extends BaseSpec {

  val longString = "x" * 920

  val data =
    s""": test stream
       |
       |data: first event
       |id: 1
       |
       |data:second event 加特林岩石
       |id
       |
       |data:  third event $longString
       |foo: bar
       |
    """.stripMargin

  val bytes = data.getBytes(UTF_8)

  val expected = Seq(
    ServerSentEvent(
      data = Some("first event"),
      id = Some("1")
    ),
    ServerSentEvent(
      data = Some("second event 加特林岩石")
    ),
    ServerSentEvent(
      data = Some(s" third event $longString")
    )
  )

  def decodeChunks(splitPos: Int) = {
    val (chunk1, chunk2) = bytes.splitAt(splitPos)
    val chunks = Seq(Unpooled.wrappedBuffer(chunk1), Unpooled.wrappedBuffer(chunk2))
    try {
      val decoder = new SseStreamDecoder
      chunks.flatMap(decoder.decodeStream)
    } finally {
      chunks.foreach(_.release())
    }
  }

  "SseStreamDecoder" should "be able to decode split UTF-8 chars" in {
    (0 until bytes.length).foreach { splitPos =>
      (splitPos, decodeChunks(splitPos)) shouldBe (splitPos, expected)
    }
  }
}
