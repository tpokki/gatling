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

package io.gatling.jms.integration

import javax.jms.{ Session => JmsSession, _ }
import scala.concurrent.duration._

import io.gatling.AkkaSpec
import io.gatling.commons.util.DefaultClock
import io.gatling.core.CoreComponents
import io.gatling.core.action.{ Action, ActorDelegatingAction }
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.controller.throttle.Throttler
import io.gatling.core.pause.Constant
import io.gatling.core.protocol.{ ProtocolComponentsRegistries, Protocols }
import io.gatling.core.session.{ Session, StaticStringExpression }
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.{ ScenarioBuilder, ScenarioContext }
import io.gatling.jms._
import io.gatling.jms.request._

import akka.actor.ActorRef
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.{ BrokerFactory, BrokerService }

object Replier {

  val Echo: PartialFunction[(Message, JmsSession), Message] = {
    case (m, _) => m
  }
}

class Replier(connectionFactory: ConnectionFactory, destination: JmsDestination, response: PartialFunction[(Message, JmsSession), Message]) {
  val t = new Thread(() => {
    val connection = connectionFactory.createConnection()
    val jmsSession = connection.createSession(false, JmsSession.AUTO_ACKNOWLEDGE)
    val consumedDestination: Destination =
      destination match {
        case JmsTemporaryQueue                      => jmsSession.createTemporaryQueue()
        case JmsTemporaryTopic                      => jmsSession.createTemporaryTopic()
        case JmsQueue(StaticStringExpression(name)) => jmsSession.createQueue(name)
        case JmsTopic(StaticStringExpression(name)) => jmsSession.createTopic(name)
        case _                                      => throw new UnsupportedOperationException("Support not implemented in this test yet")
      }

    val consumer = jmsSession.createConsumer(consumedDestination)
    val producer = jmsSession.createProducer(null)
    consumer.setMessageListener(request => {
      response.lift(request, jmsSession).foreach { response =>
        response.setJMSCorrelationID(request.getJMSCorrelationID)
        producer.send(request.getJMSReplyTo, response)
      }
    })
    connection.start()
  })

  t.start()
}

trait JmsSpec extends AkkaSpec with JmsDsl {

  override def beforeAll() = {
    sys.props += "org.apache.activemq.SERIALIZABLE_PACKAGES" -> "io.gatling"
    startBroker()
  }

  override def afterAll() = {
    super.afterAll()
    synchronized {
      cleanUpActions.foreach(_.apply())
      cleanUpActions = Nil
    }
    stopBroker()
  }

  var cleanUpActions: List[(() => Unit)] = Nil

  protected def registerCleanUpAction(f: () => Unit): Unit = synchronized {
    cleanUpActions = f :: cleanUpActions
  }

  lazy val broker: BrokerService = BrokerFactory.createBroker("broker://()/gatling?persistent=false&useJmx=false")

  def startBroker() = {
    broker.start()
    broker.waitUntilStarted()
  }

  def stopBroker() = {
    broker.stop()
    broker.waitUntilStopped()
  }

  val cf = new ActiveMQConnectionFactory("vm://gatling?broker.persistent=false&broker.useJmx=false")

  implicit val configuration = GatlingConfiguration.loadForTest()

  def jmsProtocol =
    jms
      .connectionFactory(cf)
      .matchByCorrelationId

  def runScenario(sb: ScenarioBuilder, timeout: FiniteDuration = 10.seconds, protocols: Protocols = Protocols(jmsProtocol))(
      implicit configuration: GatlingConfiguration
  ) = {
    val clock = new DefaultClock
    val coreComponents = CoreComponents(system, mock[ActorRef], mock[Throttler], mock[StatsEngine], clock, mock[Action], configuration)
    val next = new ActorDelegatingAction("next", self)
    val protocolComponentsRegistry = new ProtocolComponentsRegistries(coreComponents, protocols).scenarioRegistry(Protocols(Nil))
    val actor = sb.build(ScenarioContext(coreComponents, protocolComponentsRegistry, Constant, throttled = false), next)
    actor ! Session("TestSession", 0, clock.nowMillis)
    val session = expectMsgClass(timeout, classOf[Session])

    session
  }

  def replier(queue: JmsDestination, f: PartialFunction[(Message, JmsSession), Message]): Unit = {
    val replier = new Replier(cf, queue, f)
  }
}
