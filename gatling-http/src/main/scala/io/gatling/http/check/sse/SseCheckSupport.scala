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

package io.gatling.http.check.sse

import io.gatling.core.check._
import io.gatling.core.check.jmespath.JmesPathCheckType
import io.gatling.core.check.jsonpath.JsonPathCheckType
import io.gatling.core.check.regex.RegexCheckType
import io.gatling.core.check.string.BodyStringCheckType
import io.gatling.core.check.substring.SubstringCheckType
import io.gatling.core.json.JsonParsers

import com.fasterxml.jackson.databind.JsonNode

trait SseCheckSupport {

  implicit def checkBuilder2SseCheck[A, P, X](checkBuilder: CheckBuilder[A, P, X])(implicit materializer: CheckMaterializer[A, SseCheck, String, P]): SseCheck =
    checkBuilder.build(materializer)

  implicit def validatorCheckBuilder2SseCheck[A, P, X](
      validatorCheckBuilder: ValidatorCheckBuilder[A, P, X]
  )(implicit materializer: CheckMaterializer[A, SseCheck, String, P]): SseCheck =
    validatorCheckBuilder.exists

  implicit def findCheckBuilder2SseCheck[A, P, X](
      findCheckBuilder: FindCheckBuilder[A, P, X]
  )(implicit materializer: CheckMaterializer[A, SseCheck, String, P]): SseCheck =
    findCheckBuilder.find.exists

  implicit def sseJsonPathCheckMaterializer(implicit jsonParsers: JsonParsers): CheckMaterializer[JsonPathCheckType, SseCheck, String, JsonNode] =
    new SseJsonPathCheckMaterializer(jsonParsers)

  implicit def sseJmesPathCheckMaterializer(implicit jsonParsers: JsonParsers): CheckMaterializer[JmesPathCheckType, SseCheck, String, JsonNode] =
    new SseJmesPathCheckMaterializer(jsonParsers)

  implicit val sseRegexCheckMaterializer: CheckMaterializer[RegexCheckType, SseCheck, String, CharSequence] = SseRegexCheckMaterializer

  implicit val sseSubstringCheckMaterializer: CheckMaterializer[SubstringCheckType, SseCheck, String, String] = SseSubstringCheckMaterializer

  implicit val sseBodyStringCheckMaterializer: CheckMaterializer[BodyStringCheckType, SseCheck, String, String] = SseBodyStringCheckMaterializer
}
