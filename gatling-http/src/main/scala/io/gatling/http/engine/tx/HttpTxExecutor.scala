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

package io.gatling.http.engine.tx

import io.gatling.core.CoreComponents
import io.gatling.core.util.NameGen
import io.gatling.http.cache.{ ContentCacheEntry, HttpCaches, SslContextSupport }
import io.gatling.http.client.HttpListener
import io.gatling.http.client.util.Pair
import io.gatling.http.engine.response._
import io.gatling.http.engine.{ GatlingHttpListener, HttpEngine }
import io.gatling.http.fetch.ResourceFetcher
import io.gatling.http.protocol.HttpProtocol

import com.typesafe.scalalogging.StrictLogging

class HttpTxExecutor(
    coreComponents: CoreComponents,
    httpEngine: HttpEngine,
    httpCaches: HttpCaches,
    defaultStatsProcessor: DefaultStatsProcessor,
    httpProtocol: HttpProtocol
) extends SslContextSupport
    with NameGen
    with StrictLogging {

  import coreComponents._
  private val noopStatsProcessor = new NoopStatsProcessor(coreComponents.configuration.core.charset)

  private val resourceFetcher = new ResourceFetcher(coreComponents, httpCaches, httpProtocol, httpTxExecutor = this)

  private def executeWithCache(origTx: HttpTx)(f: HttpTx => Unit): Unit = {
    val tx = httpCaches.applyPermanentRedirect(origTx)
    val clientRequest = tx.request.clientRequest
    val uri = clientRequest.getUri

    httpCaches.contentCacheEntry(tx.session, clientRequest) match {
      case None | Some(ContentCacheEntry(None, _, _)) =>
        f(tx)

      case Some(ContentCacheEntry(Some(expire), _, _)) if clock.nowMillis > expire =>
        val newTx = tx.copy(session = httpCaches.clearContentCache(tx.session, clientRequest))
        f(newTx)

      case _ =>
        resourceFetcher.newResourceAggregatorForCachedPage(tx) match {
          case Some(aggregator) =>
            logger.info(
              s"Fetching resources of cached page request=${tx.request.requestName} uri=$uri: scenario=${tx.session.scenario}, userId=${tx.session.userId}"
            )
            aggregator.start(tx.session)

          case _ =>
            logger.info(s"Skipping cached request=${tx.request.requestName} uri=$uri: scenario=${tx.session.scenario}, userId=${tx.session.userId}")
            tx.resourceTx match {
              case Some(ResourceTx(aggregator, _)) => aggregator.onCachedResource(uri, tx)
              case _                               => tx.next ! tx.session
            }
        }
    }
  }

  private def executeHttp2WithCache(origTxs: Iterable[HttpTx])(f: Iterable[HttpTx] => Unit): Unit = {
    val cached = scala.collection.mutable.ListBuffer[HttpTx]()
    val nonCached = scala.collection.mutable.ListBuffer[HttpTx]()
    var session = origTxs.head.session

    origTxs.foreach { tx =>
      val updatedTx = httpCaches.applyPermanentRedirect(tx)
      val txCacheEntry = httpCaches.contentCacheEntry(updatedTx.session, updatedTx.request.clientRequest)
      txCacheEntry match {
        case None | Some(ContentCacheEntry(None, _, _)) =>
          nonCached += updatedTx

        case Some(ContentCacheEntry(Some(expire), _, _)) if clock.nowMillis > expire =>
          val requestToClean = updatedTx.request.clientRequest
          session = httpCaches.clearContentCache(session, requestToClean)
          nonCached += updatedTx

        case _ =>
          cached += updatedTx
      }
    }

    f(nonCached.map(_.copy(session = session)))

    cached.map(_.copy(session = session)).foreach { tx =>
      val uri = tx.request.clientRequest.getUri
      resourceFetcher.newResourceAggregatorForCachedPage(tx) match {
        case Some(aggregator) =>
          logger
            .info(s"Fetching resources of cached page request=${tx.request.requestName} uri=$uri: scenario=${tx.session.scenario}, userId=${tx.session.userId}")
          aggregator.start(tx.session)

        case _ =>
          logger.info(s"Skipping cached request=${tx.request.requestName} uri=$uri: scenario=${tx.session.scenario}, userId=${tx.session.userId}")
          tx.resourceTx match {
            case Some(ResourceTx(aggregator, _)) => aggregator.onCachedResource(uri, tx)
            case _                               =>
          }
      }
    }
  }

  def execute(origTx: HttpTx): Unit =
    execute(origTx, responseProcessorFactory)

  def execute(origTxs: Iterable[HttpTx]): Unit =
    execute(origTxs, responseProcessorFactory)

  def execute(origTx: HttpTx, responseProcessorFactory: HttpTx => ResponseProcessor): Unit =
    executeWithCache(origTx) { tx =>
      logger.debug(
        s"Sending request=${tx.request.requestName} uri=${tx.request.clientRequest.getUri}: scenario=${tx.session.scenario}, userId=${tx.session.userId}"
      )

      val ahcRequest = tx.request.clientRequest
      val clientId = tx.session.userId
      val shared = tx.request.requestConfig.httpProtocol.enginePart.shareConnections
      val listener = new GatlingHttpListener(tx, coreComponents, responseProcessorFactory(tx))
      val userSslContexts = sslContexts(tx.session)
      val sslContext = userSslContexts.map(_.sslContext).orNull
      val alpnSslContext = userSslContexts.flatMap(_.alpnSslContext).orNull

      if (tx.request.requestConfig.throttled) {
        throttler.throttle(tx.session.scenario, () => httpEngine.executeRequest(ahcRequest, clientId, shared, listener, sslContext, alpnSslContext))
      } else {
        httpEngine.executeRequest(ahcRequest, clientId, shared, listener, sslContext, alpnSslContext)
      }
    }

  def execute(origTxs: Iterable[HttpTx], responseProcessorFactory: HttpTx => ResponseProcessor): Unit = {
    executeHttp2WithCache(origTxs) { txs =>
      val headTx = txs.head
      txs.foreach(
        tx =>
          logger.debug(
            s"Sending request=${tx.request.requestName} uri=${tx.request.clientRequest.getUri} scenario=${tx.session.scenario}, userId=${tx.session.userId}"
          )
      )
      val requestsAndListeners = txs.map { tx =>
        val listener: HttpListener = new GatlingHttpListener(tx, coreComponents, responseProcessorFactory(tx))
        new Pair(tx.request.clientRequest, listener)
      }
      val clientId = headTx.session.userId
      val shared = headTx.request.requestConfig.httpProtocol.enginePart.shareConnections
      val userSslContexts = sslContexts(headTx.session)
      val sslContext = userSslContexts.map(_.sslContext).orNull
      val alpnSslContext = userSslContexts.flatMap(_.alpnSslContext).orNull

      if (txs.head.request.requestConfig.throttled) {
        throttler.throttle(headTx.session.scenario, () => httpEngine.executeHttp2Requests(requestsAndListeners, clientId, shared, sslContext, alpnSslContext))
      } else {
        httpEngine.executeHttp2Requests(requestsAndListeners, clientId, shared, sslContext, alpnSslContext)
      }
    }
  }

  private val responseProcessorFactory: HttpTx => ResponseProcessor = tx =>
    tx.resourceTx match {
      case Some(resourceTx) => newResourceResponseProcessor(tx, resourceTx)
      case _                => newRootResponseProcessor(tx)
    }

  private def newRootResponseProcessor(tx: HttpTx): ResponseProcessor =
    new DefaultResponseProcessor(
      tx,
      sessionProcessor = new RootSessionProcessor(
        tx.silent,
        tx.request.clientRequest,
        tx.request.requestConfig.checks,
        httpCaches,
        httpProtocol,
        clock
      ),
      statsProcessor = statsProcessor(tx),
      nextExecutor = new RootNextExecutor(tx, clock, resourceFetcher, this),
      configuration.core.charset
    )

  private def newResourceResponseProcessor(tx: HttpTx, resourceTx: ResourceTx): ResponseProcessor =
    new DefaultResponseProcessor(
      tx = tx.copy(session = resourceTx.aggregator.currentSession),
      sessionProcessor = new ResourceSessionProcessor(
        tx.silent,
        tx.request.clientRequest,
        tx.request.requestConfig.checks,
        httpCaches,
        httpProtocol,
        clock
      ),
      statsProcessor = statsProcessor(tx),
      nextExecutor = new ResourceNextExecutor(tx, resourceTx),
      configuration.core.charset
    )

  def statsProcessor(tx: HttpTx): StatsProcessor =
    if (tx.silent) noopStatsProcessor else defaultStatsProcessor
}
