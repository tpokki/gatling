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

package io.gatling.core.body

import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.{ Expression, StaticStringExpression }
import io.gatling.core.util.{ Resource, ResourceCache }
import io.gatling.core.util.cache.Cache

import com.github.benmanes.caffeine.cache.LoadingCache

final case class ResourceAndCachedBytes(resource: Resource, cachedBytes: Option[Array[Byte]])

class RawFileBodies(implicit configuration: GatlingConfiguration) extends ResourceCache {

  private val bytesCache: LoadingCache[Resource, Option[Array[Byte]]] = {
    val resourceToBytes: Resource => Option[Array[Byte]] = resource =>
      if (resource.file.length > configuration.core.rawFileBodiesInMemoryMaxSize) {
        None
      } else {
        Some(resource.bytes)
      }

    Cache.newConcurrentLoadingCache(configuration.core.rawFileBodiesCacheMaxCapacity, resourceToBytes)
  }

  // FIXME cache ResourceAndCachedBytes
  def asResourceAndCachedBytes(filePath: Expression[String]): Expression[ResourceAndCachedBytes] =
    filePath match {
      case StaticStringExpression(path) =>
        val resourceAndCachedBytes =
          for {
            resource <- cachedResource(path)
          } yield ResourceAndCachedBytes(resource, Some(resource.bytes))

        _ => resourceAndCachedBytes

      case _ =>
        session =>
          for {
            path <- filePath(session)
            resource <- cachedResource(path)
          } yield ResourceAndCachedBytes(resource, bytesCache.get(resource))
    }
}
