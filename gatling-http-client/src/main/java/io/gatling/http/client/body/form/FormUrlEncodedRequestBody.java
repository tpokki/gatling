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

package io.gatling.http.client.body.form;

import io.gatling.http.client.Param;
import io.gatling.http.client.body.RequestBody;
import io.gatling.http.client.body.RequestBodyBuilder;
import io.gatling.http.client.body.WritableContent;
import io.gatling.netty.util.StringBuilderPool;
import io.gatling.netty.util.Utf8UrlEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FormUrlEncodedRequestBody extends RequestBody<List<Param>> {

  public FormUrlEncodedRequestBody(List<Param> content, String contentType, Charset charset) {
    super(content, contentType, charset);
  }

  @Override
  public WritableContent build(boolean zeroCopy, ByteBufAllocator alloc) {

    StringBuilder sb = encode();

    ByteBuf bb = ByteBufUtil.writeAscii(alloc, sb);
    return new WritableContent(bb, bb.readableBytes());
  }

  private StringBuilder encode() {
    StringBuilder sb = StringBuilderPool.DEFAULT.get();

    for (Param param : content) {
      encodeAndAppendFormParam(sb, param.getName(), param.getValue(), charset);
    }
    sb.setLength(sb.length() - 1);
    return sb;
  }

  private static void encodeAndAppendFormParam(StringBuilder sb, String name, String value, Charset charset) {
    encodeAndAppendFormField(sb, name, charset);
    if (value != null) {
      sb.append('=');
      encodeAndAppendFormField(sb, value, charset);
    }
    sb.append('&');
  }

  private static void encodeAndAppendFormField(StringBuilder sb, String field, Charset charset) {
    if (charset.equals(UTF_8)) {
      Utf8UrlEncoder.encodeAndAppendFormElement(sb, field);
    } else {
      try {
        // TODO there's probably room for perf improvements
        sb.append(URLEncoder.encode(field, charset.name()));
      } catch (UnsupportedEncodingException e) {
        // can't happen, as Charset was already resolved
      }
    }
  }

  @Override
  public RequestBodyBuilder<List<Param>> newBuilder() {
    return new FormUrlEncodedRequestBodyBuilder(content);
  }


  @Override
  public byte[] getBytes() {
    return encode().toString().getBytes();
  }

  @Override
  public String toString() {
    return "FormUrlEncodedRequestBody{" +
      "content=" + content +
      ", contentType=" + contentType +
      ", charset=" + charset +
      '}';
  }
}
