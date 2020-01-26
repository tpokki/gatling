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

package io.gatling.core.util

import io.gatling.commons.util.TypeCaster
import io.gatling.commons.validation._

import akka.actor.ActorRef

object CoreTypeCaster {

  implicit val ActorRefTypeCaster: TypeCaster[ActorRef] = new TypeCaster[ActorRef] {
    @throws[ClassCastException]
    override def cast(value: Any): ActorRef =
      value match {
        case v: ActorRef => v
        case _           => throw new ClassCastException(cceMessage(value, classOf[ActorRef]))
      }

    override def validate(value: Any): Validation[ActorRef] =
      value match {
        case v: ActorRef => v.success
        case _           => cceMessage(value, classOf[ActorRef]).failure
      }
  }
}
