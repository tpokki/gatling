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

package io.gatling.core.scenario

import scala.concurrent.duration.{ Duration, FiniteDuration }

import io.gatling.commons.stats.assertion.Assertion
import io.gatling.core.CoreComponents
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.controller.throttle.{ ThrottleStep, Throttling, Throttlings }
import io.gatling.core.pause._
import io.gatling.core.protocol.{ Protocol, ProtocolComponentsRegistries, Protocols }
import io.gatling.core.session.Expression
import io.gatling.core.structure.PopulationBuilder

abstract class Simulation {

  private var _populationBuilders: List[PopulationBuilder] = Nil
  private var _globalProtocols: Protocols = Protocols()
  private var _assertions = Seq.empty[Assertion]
  private var _maxDuration: Option[FiniteDuration] = None
  private var _globalPauseType: PauseType = Constant
  private var _globalThrottleSteps: Iterable[ThrottleStep] = Nil
  private var _beforeSteps: List[() => Unit] = Nil
  private var _afterSteps: List[() => Unit] = Nil

  def before(step: => Unit): Unit =
    _beforeSteps = _beforeSteps ::: List(() => step)

  def setUp(populationBuilders: PopulationBuilder*): SetUp = setUp(populationBuilders.toList)

  def setUp(populationBuilders: List[PopulationBuilder]): SetUp = {
    if (_populationBuilders.nonEmpty)
      throw new UnsupportedOperationException("setUp can only be called once")
    _populationBuilders = populationBuilders
    new SetUp
  }

  def after(step: => Unit): Unit =
    _afterSteps = _afterSteps ::: List(() => step)

  class SetUp {

    def protocols(ps: Protocol*): SetUp = protocols(ps.toIterable)

    def protocols(ps: Iterable[Protocol]): SetUp = {
      _globalProtocols = _globalProtocols ++ ps
      this
    }

    def assertions(asserts: Assertion*): SetUp = assertions(asserts.toIterable)

    def assertions(asserts: Iterable[Assertion]): SetUp = {
      _assertions = _assertions ++ asserts
      this
    }

    def maxDuration(duration: FiniteDuration): SetUp = {
      _maxDuration = Some(duration)
      this
    }

    def throttle(throttleSteps: ThrottleStep*): SetUp = throttle(throttleSteps.toIterable)

    def throttle(throttleSteps: Iterable[ThrottleStep]): SetUp = {
      _globalThrottleSteps = throttleSteps
      this
    }

    def disablePauses: SetUp = pauses(Disabled)
    def constantPauses: SetUp = pauses(Constant)
    def exponentialPauses: SetUp = pauses(Exponential)
    def customPauses(custom: Expression[Long]): SetUp = pauses(Custom(custom))
    def uniformPauses(plusOrMinus: Double): SetUp = pauses(UniformPercentage(plusOrMinus))
    def uniformPauses(plusOrMinus: Duration): SetUp = pauses(UniformDuration(plusOrMinus))
    def pauses(pauseType: PauseType): SetUp = {
      _globalPauseType = pauseType
      this
    }
  }

  private def resolvePopulationBuilders(populationBuilders: List[PopulationBuilder], configuration: GatlingConfiguration): List[PopulationBuilder] =
    configuration.resolve(
      // [fl]
      //
      //
      //
      //
      // [fl]
      _populationBuilders
    )

  private def resolveThrottleSteps(steps: Iterable[ThrottleStep], configuration: GatlingConfiguration): Iterable[ThrottleStep] =
    configuration.resolve(
      // [fl]
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
      // [fl]
      steps
    )

  private[gatling] def params(configuration: GatlingConfiguration): SimulationParams = {

    require(_populationBuilders.nonEmpty, "No scenario set up")
    val duplicates = _populationBuilders.groupBy(_.scenarioBuilder.name).collect { case (name, scns) if scns.size > 1 => name }
    require(duplicates.isEmpty, s"Scenario names must be unique but found duplicates: $duplicates")
    _populationBuilders.foreach(scn => require(scn.scenarioBuilder.actionBuilders.nonEmpty, s"Scenario ${scn.scenarioBuilder.name} is empty"))

    val populationBuilders = resolvePopulationBuilders(_populationBuilders, configuration)

    val scenarioThrottlings: Map[String, Throttling] = populationBuilders.flatMap { scn =>
      val steps = resolveThrottleSteps(scn.scenarioThrottleSteps, configuration)

      if (steps.isEmpty) {
        None
      } else {
        Some(scn.scenarioBuilder.name -> Throttling(steps))
      }
    }.toMap

    val globalThrottling =
      if (_globalThrottleSteps.isEmpty) {
        None
      } else {
        Some(Throttling(resolveThrottleSteps(_globalThrottleSteps, configuration)))
      }

    val maxDuration = {

      val globalThrottlingMaxDuration = globalThrottling.map(_.duration)
      val scenarioThrottlingMaxDurations = scenarioThrottlings.values.map(_.duration).toList

      _maxDuration.map(List(_)).getOrElse(Nil) ::: globalThrottlingMaxDuration.map(List(_)).getOrElse(Nil) ::: scenarioThrottlingMaxDurations match {
        case Nil => None
        case nel => Some(nel.min)
      }
    }

    SimulationParams(
      getClass.getName,
      populationBuilders,
      _globalProtocols,
      _globalPauseType,
      Throttlings(globalThrottling, scenarioThrottlings),
      maxDuration,
      _assertions
    )
  }

  private[gatling] def executeBefore(): Unit = _beforeSteps.foreach(_.apply())
  private[gatling] def executeAfter(): Unit = _afterSteps.foreach(_.apply())
}

final case class SimulationParams(
    name: String,
    populationBuilders: List[PopulationBuilder],
    globalProtocols: Protocols,
    globalPauseType: PauseType,
    throttlings: Throttlings,
    maxDuration: Option[FiniteDuration],
    assertions: Seq[Assertion]
) {

  def scenarios(coreComponents: CoreComponents): List[Scenario] = {
    val protocolComponentsRegistries = new ProtocolComponentsRegistries(coreComponents, globalProtocols)
    populationBuilders.map(_.build(coreComponents, protocolComponentsRegistries, globalPauseType, throttlings.global))
  }
}
