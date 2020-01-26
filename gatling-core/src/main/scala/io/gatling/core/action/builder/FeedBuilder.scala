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

import java.util.{ IdentityHashMap => JIdentityHashMap }

import scala.collection.JavaConverters._

import io.gatling.core.action.{ Action, Feed, SingletonFeed }
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.session.Expression
import io.gatling.core.util.NameGen

import akka.actor.ActorRef

object FeedBuilder {
  private val Instances = new JIdentityHashMap[FeederBuilder, ActorRef].asScala
}

class FeedBuilder(feederBuilder: FeederBuilder, number: Expression[Int]) extends ActionBuilder with NameGen {

  private[this] def newSingletonFeed(ctx: ScenarioContext): ActorRef = {
    val props = SingletonFeed.props(feederBuilder.apply, ctx.coreComponents.controller)
    ctx.coreComponents.actorSystem.actorOf(props, genName("singletonFeed"))
  }

  override def build(ctx: ScenarioContext, next: Action): Action = {
    val feederSingleton = FeedBuilder.Instances.getOrElseUpdate(feederBuilder, newSingletonFeed(ctx))
    new Feed(feederSingleton, number, ctx.coreComponents.statsEngine, ctx.coreComponents.clock, next)
  }
}
