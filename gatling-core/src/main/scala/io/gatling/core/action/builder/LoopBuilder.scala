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

package io.gatling.core.action.builder

import io.gatling.commons.validation._
import io.gatling.core.action.{ Action, Loop }
import io.gatling.core.session.{ Expression, Session }
import io.gatling.core.structure.{ ChainBuilder, ScenarioContext }
import io.gatling.core.util.NameGen

sealed abstract class LoopType(val name: String, val timeBased: Boolean, val evaluateConditionAfterLoop: Boolean)
case object RepeatLoopType extends LoopType("repeat", false, false)
case object ForeachLoopType extends LoopType("foreach", false, false)
case object DuringLoopType extends LoopType("during", true, false)
case object ForeverLoopType extends LoopType("forever", false, false)
case object AsLongAsLoopType extends LoopType("asLongAs", false, false)
case object DoWhileType extends LoopType("doWhile", false, true)
case object AsLongAsDuringLoopType extends LoopType("asLongAsDuring", true, false)
case object DoWhileDuringType extends LoopType("doWhileDuring", true, true)

/**
 * @constructor create a new Loop
 * @param condition the function that determine the condition
 * @param loopNext chain that will be executed if condition evaluates to true
 * @param counterName the name of the loop counter
 * @param exitASAP if the loop is to be exited as soon as the condition no longer holds
 * @param loopType the loop type
 */
class LoopBuilder(condition: Expression[Boolean], loopNext: ChainBuilder, counterName: String, exitASAP: Boolean, loopType: LoopType)
    extends ActionBuilder
    with NameGen {

  def build(ctx: ScenarioContext, next: Action): Action = {
    import ctx._
    val safeCondition = condition.safe
    val actualCondition =
      if (loopType.evaluateConditionAfterLoop) { session: Session =>
        if (session.attributes(counterName) == 0) {
          TrueSuccess
        } else {
          safeCondition(session)
        }
      } else {
        safeCondition
      }

    val loopAction =
      new Loop(actualCondition, counterName, exitASAP, loopType.timeBased, coreComponents.statsEngine, ctx.coreComponents.clock, genName(loopType.name), next)
    val loopNextAction = loopNext.build(ctx, loopAction)
    loopAction.initialize(loopNextAction, ctx.coreComponents.actorSystem)
    loopAction
  }
}
