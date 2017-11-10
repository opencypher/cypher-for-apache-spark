/*
 * Copyright (c) 2016-2017 "Neo4j, Inc." [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opencypher.caps.impl

import scala.collection.mutable

trait DirectCompilationStage[-A, B, C] extends CompilationStage[A, B, C] {
  final override type Out = B
  final override def extract(output: Out): B = output
}

//abstract class CachingDirectCompilationStage[A, B, C <: CachingContext[A, B]] extends DirectCompilationStage[A, B, C] {
//  final override def process(input: A)(implicit context: C): B = {
//    context.cache.get(input) match {
//      case None =>
//        val output = cachingProcess(input)
//        context.cache(input) = output
//        output
//
//      case Some(output) =>
//        println(s"Cache Hit $input -> $output")
//        output
//    }
//  }
//
//  def onCacheHit(input: A, output: B): Unit = {}
//
//  def cachingProcess(input: A)(implicit context: C): B
//}
//
//abstract class CachingContext[I, O] {
//  val cache: mutable.Map[I, O] = mutable.Map.empty
//}

