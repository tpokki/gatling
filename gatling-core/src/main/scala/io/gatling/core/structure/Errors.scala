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

package io.gatling.core.structure

import java.util.UUID

import io.gatling.core.action.builder.{ ExitHereIfFailedBuilder, TryMaxBuilder }
import io.gatling.core.session._

import com.eatthepath.uuid.FastUUID

private[structure] trait Errors[B] extends Execs[B] {

  def exitBlockOnFail(chain: ChainBuilder): B = tryMax(1.expressionSuccess)(chain)
  def tryMax(times: Expression[Int], counterName: String = FastUUID.toString(UUID.randomUUID))(chain: ChainBuilder): B = {

    exec(new TryMaxBuilder(times, counterName, chain))
  }

  def exitHereIfFailed: B = exec(ExitHereIfFailedBuilder)
}
