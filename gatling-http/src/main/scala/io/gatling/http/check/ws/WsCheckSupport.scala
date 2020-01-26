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

package io.gatling.http.check.ws

import io.gatling.core.check._
import io.gatling.core.check.bytes.BodyBytesCheckType
import io.gatling.core.check.jmespath.JmesPathCheckType
import io.gatling.core.check.jsonpath.JsonPathCheckType
import io.gatling.core.check.regex.RegexCheckType
import io.gatling.core.check.string.BodyStringCheckType
import io.gatling.core.check.substring.SubstringCheckType
import io.gatling.core.json.JsonParsers

import com.fasterxml.jackson.databind.JsonNode

trait WsCheckSupport {

  implicit def checkBuilder2WsTextCheck[A, P, X](
      checkBuilder: CheckBuilder[A, P, X]
  )(implicit materializer: CheckMaterializer[A, WsTextCheck, String, P]): WsTextCheck =
    checkBuilder.build(materializer)

  implicit def validatorCheckBuilder2WsTextCheck[A, P, X](
      validatorCheckBuilder: ValidatorCheckBuilder[A, P, X]
  )(implicit materializer: CheckMaterializer[A, WsTextCheck, String, P]): WsTextCheck =
    validatorCheckBuilder.exists

  implicit def findCheckBuilder2WsTextCheck[A, P, X](
      findCheckBuilder: FindCheckBuilder[A, P, X]
  )(implicit materializer: CheckMaterializer[A, WsTextCheck, String, P]): WsTextCheck =
    findCheckBuilder.find.exists

  implicit def checkBuilder2WsBinaryCheck[A, P, X](
      checkBuilder: CheckBuilder[A, P, X]
  )(implicit materializer: CheckMaterializer[A, WsBinaryCheck, Array[Byte], P]): WsBinaryCheck =
    checkBuilder.build(materializer)

  implicit def validatorCheckBuilder2WsBinaryCheck[A, P, X](
      validatorCheckBuilder: ValidatorCheckBuilder[A, P, X]
  )(implicit materializer: CheckMaterializer[A, WsBinaryCheck, Array[Byte], P]): WsBinaryCheck =
    validatorCheckBuilder.exists

  implicit def findCheckBuilder2WsBinaryCheck[A, P, X](
      findCheckBuilder: FindCheckBuilder[A, P, X]
  )(implicit materializer: CheckMaterializer[A, WsBinaryCheck, Array[Byte], P]): WsBinaryCheck =
    findCheckBuilder.find.exists

  implicit def wsJsonPathCheckMaterializer(implicit jsonParsers: JsonParsers): CheckMaterializer[JsonPathCheckType, WsTextCheck, String, JsonNode] =
    new WsJsonPathCheckMaterializer(jsonParsers)

  implicit def wsJmesPathCheckMaterializer(implicit jsonParsers: JsonParsers): CheckMaterializer[JmesPathCheckType, WsTextCheck, String, JsonNode] =
    new WsJmesPathCheckMaterializer(jsonParsers)

  implicit val wsRegexCheckMaterializer: CheckMaterializer[RegexCheckType, WsTextCheck, String, CharSequence] = WsRegexCheckMaterializer

  implicit val wsBodyStringCheckMaterializer: CheckMaterializer[BodyStringCheckType, WsTextCheck, String, String] = WsBodyStringCheckMaterializer

  implicit val wsSubstringCheckMaterializer: CheckMaterializer[SubstringCheckType, WsTextCheck, String, String] = WsSubstringCheckMaterializer

  implicit val wsBodyBytesCheckMaterializer: CheckMaterializer[BodyBytesCheckType, WsBinaryCheck, Array[Byte], Array[Byte]] = WsBodyBytesCheckMaterializer
}
